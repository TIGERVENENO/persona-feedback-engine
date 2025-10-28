package ru.tigran.personafeedbackengine.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tigran.personafeedbackengine.config.RabbitMQConfig;
import ru.tigran.personafeedbackengine.dto.FeedbackSessionRequest;
import ru.tigran.personafeedbackengine.dto.FeedbackGenerationTask;
import ru.tigran.personafeedbackengine.dto.PersonaVariation;
import ru.tigran.personafeedbackengine.exception.ErrorCode;
import ru.tigran.personafeedbackengine.exception.UnauthorizedException;
import ru.tigran.personafeedbackengine.exception.ValidationException;
import ru.tigran.personafeedbackengine.model.FeedbackResult;
import ru.tigran.personafeedbackengine.model.FeedbackSession;
import ru.tigran.personafeedbackengine.model.Persona;
import ru.tigran.personafeedbackengine.model.Product;
import ru.tigran.personafeedbackengine.model.User;
import ru.tigran.personafeedbackengine.repository.FeedbackResultRepository;
import ru.tigran.personafeedbackengine.repository.FeedbackSessionRepository;
import ru.tigran.personafeedbackengine.repository.PersonaRepository;
import ru.tigran.personafeedbackengine.repository.ProductRepository;
import ru.tigran.personafeedbackengine.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FeedbackService {

    private final FeedbackSessionRepository feedbackSessionRepository;
    private final FeedbackResultRepository feedbackResultRepository;
    private final ProductRepository productRepository;
    private final PersonaRepository personaRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;
    private final PersonaService personaService;
    private final PersonaVariationService personaVariationService;
    private final int maxProductsPerSession;
    private final int maxPersonasPerSession;
    private final Counter feedbackSessionInitiatedCounter;
    private final Timer feedbackSessionTimer;

    public FeedbackService(
            FeedbackSessionRepository feedbackSessionRepository,
            FeedbackResultRepository feedbackResultRepository,
            ProductRepository productRepository,
            PersonaRepository personaRepository,
            UserRepository userRepository,
            RabbitTemplate rabbitTemplate,
            PersonaService personaService,
            PersonaVariationService personaVariationService,
            @Value("${app.feedback.max-products-per-session}") int maxProductsPerSession,
            @Value("${app.feedback.max-personas-per-session}") int maxPersonasPerSession,
            MeterRegistry meterRegistry
    ) {
        this.feedbackSessionRepository = feedbackSessionRepository;
        this.feedbackResultRepository = feedbackResultRepository;
        this.productRepository = productRepository;
        this.personaRepository = personaRepository;
        this.userRepository = userRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.personaService = personaService;
        this.personaVariationService = personaVariationService;
        this.maxProductsPerSession = maxProductsPerSession;
        this.maxPersonasPerSession = maxPersonasPerSession;
        this.feedbackSessionInitiatedCounter = Counter.builder("feedback.session.initiated")
                .description("Total feedback sessions initiated")
                .register(meterRegistry);
        this.feedbackSessionTimer = Timer.builder("feedback.session.time")
                .description("Time to initiate feedback session")
                .register(meterRegistry);
    }

    /**
     * Starts a feedback session workflow.
     * Creates a FeedbackSession with multiple FeedbackResult entities (one per product-persona pair)
     * and publishes tasks to the queue.
     */
    @Transactional
    public Long startFeedbackSession(Long userId, FeedbackSessionRequest request) {
        try {
            return feedbackSessionTimer.recordCallable(() -> executeStartFeedbackSession(userId, request));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Long executeStartFeedbackSession(Long userId, FeedbackSessionRequest request) {
        log.info("Starting feedback session for user {}", userId);

        // Валидация количества продуктов
        if (request.getProductIds().size() > maxProductsPerSession) {
            throw new ValidationException(
                    "Too many products. Maximum is " + maxProductsPerSession,
                    "TOO_MANY_PRODUCTS"
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ValidationException("User not found", "USER_NOT_FOUND"));

        // Загружаем продукты
        List<Product> products = productRepository.findByUserIdAndIdIn(userId, request.getProductIds());
        if (products.size() != request.getProductIds().size()) {
            throw new UnauthorizedException(
                    "Some products do not belong to this user or do not exist",
                    "UNAUTHORIZED_ACCESS"
            );
        }

        // Определяем режим работы и получаем список persona IDs
        List<Long> personaIds;
        if (request.getPersonaIds() != null && !request.getPersonaIds().isEmpty()) {
            // Режим 1: Явно указанные personaIds (старый способ)
            log.info("Using explicit persona IDs mode: {}", request.getPersonaIds());
            personaIds = request.getPersonaIds();

            if (personaIds.size() > maxPersonasPerSession) {
                throw new ValidationException(
                        "Too many personas. Maximum is " + maxPersonasPerSession,
                        "TOO_MANY_PERSONAS"
                );
            }
        } else if (request.getTargetAudience() != null) {
            // Режим 2: Автогенерация из targetAudience (новый способ)
            log.info("Using target audience mode with {} personas", request.getPersonaCount());

            // Генерируем вариации
            List<PersonaVariation> variations = personaVariationService.generateVariations(
                    request.getTargetAudience(),
                    request.getPersonaCount()
            );

            // Находим или создаём персоны
            personaIds = personaService.findOrCreatePersonas(userId, variations);
            log.info("Found/created {} personas: {}", personaIds.size(), personaIds);

            // Ждём пока все персоны будут готовы (ACTIVE)
            waitForPersonasReady(userId, personaIds, 60); // 60 секунд таймаут
        } else {
            throw new ValidationException(
                    "Either personaIds or targetAudience must be provided",
                    ErrorCode.VALIDATION_ERROR.getCode()
            );
        }

        // Загружаем персоны и валидируем
        List<Persona> personas = personaRepository.findByUserIdAndIdIn(userId, personaIds);
        if (personas.size() != personaIds.size()) {
            throw new UnauthorizedException(
                    "Some personas do not belong to this user or do not exist",
                    "UNAUTHORIZED_ACCESS"
            );
        }

        validatePersonasAreReady(personas);

        // Создаём feedback session
        FeedbackSession session = new FeedbackSession();
        session.setUser(user);
        session.setStatus(FeedbackSession.FeedbackSessionStatus.PENDING);
        session.setLanguage(request.getLanguage().toUpperCase());
        session.setFeedbackResults(new ArrayList<>());

        FeedbackSession savedSession = feedbackSessionRepository.save(session);
        log.info("Created feedback session with id {}", savedSession.getId());

        // Создаём feedback results (product x persona)
        List<FeedbackResult> results = new ArrayList<>();
        for (Product product : products) {
            for (Persona persona : personas) {
                FeedbackResult result = new FeedbackResult();
                result.setFeedbackSession(savedSession);
                result.setProduct(product);
                result.setPersona(persona);
                result.setStatus(FeedbackResult.FeedbackResultStatus.PENDING);
                results.add(result);
            }
        }

        List<FeedbackResult> savedResults = feedbackResultRepository.saveAll(results);
        log.info("Batch created {} feedback results for session {}", savedResults.size(), savedSession.getId());

        // Публикуем задачи на генерацию
        for (FeedbackResult savedResult : savedResults) {
            FeedbackGenerationTask task = new FeedbackGenerationTask(
                    savedResult.getId(),
                    savedResult.getProduct().getId(),
                    savedResult.getPersona().getId(),
                    savedSession.getLanguage()
            );
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    "feedback.generation",
                    task
            );
        }
        log.info("Published {} feedback generation tasks for session {}", savedResults.size(), savedSession.getId());

        feedbackSessionInitiatedCounter.increment();
        return savedSession.getId();
    }

    /**
     * Ждёт пока все персоны станут ACTIVE (готовы для генерации фидбека).
     * Использует polling с интервалом 2 секунды.
     *
     * @param userId     ID пользователя
     * @param personaIds Список ID персон
     * @param timeoutSec Максимальное время ожидания в секундах
     * @throws ValidationException если таймаут истёк или персоны FAILED
     */
    private void waitForPersonasReady(Long userId, List<Long> personaIds, int timeoutSec) {
        log.info("Waiting for {} personas to become ACTIVE (timeout: {}s)", personaIds.size(), timeoutSec);

        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeoutSec * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            List<Persona> personas = personaRepository.findByUserIdAndIdIn(userId, personaIds);

            long generatingCount = personas.stream()
                    .filter(p -> p.getStatus() == Persona.PersonaStatus.GENERATING)
                    .count();

            long failedCount = personas.stream()
                    .filter(p -> p.getStatus() == Persona.PersonaStatus.FAILED)
                    .count();

            if (failedCount > 0) {
                throw new ValidationException(
                        "Some personas failed to generate",
                        ErrorCode.PERSONA_GENERATION_FAILED.getCode()
                );
            }

            if (generatingCount == 0) {
                log.info("All {} personas are now ACTIVE", personas.size());
                return; // Все персоны готовы
            }

            log.debug("Waiting for {} personas to complete generation...", generatingCount);
            try {
                Thread.sleep(2000); // Ждём 2 секунды перед следующей проверкой
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ValidationException(
                        "Interrupted while waiting for personas",
                        ErrorCode.INTERNAL_SERVER_ERROR.getCode()
                );
            }
        }

        // Таймаут истёк
        throw new ValidationException(
                "Timeout waiting for personas to be generated. Please try again later.",
                ErrorCode.PERSONA_GENERATION_TIMEOUT.getCode()
        );
    }

    /**
     * Validates that all personas are in ACTIVE status (ready for feedback generation).
     *
     * Personas must be fully generated before being used in feedback sessions.
     * Statuses:
     * - GENERATING: Still being processed by AI, cannot be used yet
     * - ACTIVE: Ready to use, has all required fields populated
     * - FAILED: Generation failed, cannot be used
     *
     * @param personas List of personas to validate
     * @throws ValidationException if any persona is not ACTIVE
     */
    private void validatePersonasAreReady(List<Persona> personas) {
        List<Persona> notReadyPersonas = personas.stream()
            .filter(p -> p.getStatus() != Persona.PersonaStatus.ACTIVE)
            .toList();

        if (!notReadyPersonas.isEmpty()) {
            String statusDetails = notReadyPersonas.stream()
                .map(p -> "ID:" + p.getId() + " Status:" + p.getStatus())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

            String message = String.format(
                "Some personas are not ready for feedback generation. " +
                "Required status: ACTIVE. Found: %s",
                statusDetails
            );
            log.warn("Persona readiness validation failed: {}", message);
            throw new ValidationException(message, ErrorCode.PERSONAS_NOT_READY.getCode());
        }

        log.debug("All {} personas are ready for feedback generation", personas.size());
    }
}
