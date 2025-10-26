package ru.tigran.personafeedbackengine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tigran.personafeedbackengine.config.RabbitMQConfig;
import ru.tigran.personafeedbackengine.dto.FeedbackSessionRequest;
import ru.tigran.personafeedbackengine.dto.FeedbackGenerationTask;
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
    private final int maxProductsPerSession;
    private final int maxPersonasPerSession;

    public FeedbackService(
            FeedbackSessionRepository feedbackSessionRepository,
            FeedbackResultRepository feedbackResultRepository,
            ProductRepository productRepository,
            PersonaRepository personaRepository,
            UserRepository userRepository,
            RabbitTemplate rabbitTemplate,
            @Value("${app.feedback.max-products-per-session}") int maxProductsPerSession,
            @Value("${app.feedback.max-personas-per-session}") int maxPersonasPerSession
    ) {
        this.feedbackSessionRepository = feedbackSessionRepository;
        this.feedbackResultRepository = feedbackResultRepository;
        this.productRepository = productRepository;
        this.personaRepository = personaRepository;
        this.userRepository = userRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.maxProductsPerSession = maxProductsPerSession;
        this.maxPersonasPerSession = maxPersonasPerSession;
    }

    /**
     * Starts a feedback session workflow.
     * Creates a FeedbackSession with multiple FeedbackResult entities (one per product-persona pair)
     * and publishes tasks to the queue.
     */
    @Transactional
    public Long startFeedbackSession(Long userId, FeedbackSessionRequest request) {
        log.info("Starting feedback session for user {}", userId);

        // Validate request
        if (request.productIds().size() > maxProductsPerSession) {
            throw new ValidationException(
                    "Too many products. Maximum is " + maxProductsPerSession,
                    "TOO_MANY_PRODUCTS"
            );
        }
        if (request.personaIds().size() > maxPersonasPerSession) {
            throw new ValidationException(
                    "Too many personas. Maximum is " + maxPersonasPerSession,
                    "TOO_MANY_PERSONAS"
            );
        }

        // Verify user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ValidationException("User not found", "USER_NOT_FOUND"));

        // Fetch and validate products (ownership check)
        List<Product> products = productRepository.findByUserIdAndIdIn(userId, request.productIds());
        if (products.size() != request.productIds().size()) {
            throw new UnauthorizedException(
                    "Some products do not belong to this user or do not exist",
                    "UNAUTHORIZED_ACCESS"
            );
        }

        // Fetch and validate personas (ownership check)
        List<Persona> personas = personaRepository.findByUserIdAndIdIn(userId, request.personaIds());
        if (personas.size() != request.personaIds().size()) {
            throw new UnauthorizedException(
                    "Some personas do not belong to this user or do not exist",
                    "UNAUTHORIZED_ACCESS"
            );
        }

        validatePersonasAreReady(personas);

        // Create FeedbackSession
        FeedbackSession session = new FeedbackSession();
        session.setUser(user);
        session.setStatus(FeedbackSession.FeedbackSessionStatus.PENDING);
        session.setFeedbackResults(new ArrayList<>());

        FeedbackSession savedSession = feedbackSessionRepository.save(session);
        log.info("Created feedback session with id {}", savedSession.getId());

        // Create FeedbackResult entities and publish tasks
        for (Product product : products) {
            for (Persona persona : personas) {
                FeedbackResult result = new FeedbackResult();
                result.setFeedbackSession(savedSession);
                result.setProduct(product);
                result.setPersona(persona);
                result.setStatus(FeedbackResult.FeedbackResultStatus.PENDING);

                FeedbackResult savedResult = feedbackResultRepository.save(result);
                log.info("Created feedback result with id {}", savedResult.getId());

                // Publish task to queue
                FeedbackGenerationTask task = new FeedbackGenerationTask(
                        savedResult.getId(),
                        product.getId(),
                        persona.getId()
                );
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_NAME,
                        "feedback.generation",
                        task
                );
                log.info("Published feedback generation task for result {}", savedResult.getId());
            }
        }

        return savedSession.getId();
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
            throw new ValidationException(message, "PERSONAS_NOT_READY");
        }

        log.debug("All {} personas are ready for feedback generation", personas.size());
    }
}
