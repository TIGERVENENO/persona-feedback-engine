package ru.tigran.personafeedbackengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tigran.personafeedbackengine.config.RabbitMQConfig;
import ru.tigran.personafeedbackengine.dto.PersonaDemographics;
import ru.tigran.personafeedbackengine.dto.PersonaGenerationRequest;
import ru.tigran.personafeedbackengine.dto.PersonaGenerationTask;
import ru.tigran.personafeedbackengine.dto.PersonaPsychographics;
import ru.tigran.personafeedbackengine.dto.PersonaVariation;
import ru.tigran.personafeedbackengine.exception.ErrorCode;
import ru.tigran.personafeedbackengine.exception.ValidationException;
import ru.tigran.personafeedbackengine.model.Persona;
import ru.tigran.personafeedbackengine.model.User;
import ru.tigran.personafeedbackengine.repository.PersonaRepository;
import ru.tigran.personafeedbackengine.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class PersonaService {

    private final PersonaRepository personaRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Counter personaGenerationInitiatedCounter;
    private final Timer personaGenerationTimer;

    public PersonaService(
            PersonaRepository personaRepository,
            UserRepository userRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.personaRepository = personaRepository;
        this.userRepository = userRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.personaGenerationInitiatedCounter = Counter.builder("persona.generation.initiated")
                .description("Total personas initiated for generation")
                .register(meterRegistry);
        this.personaGenerationTimer = Timer.builder("persona.generation.time")
                .description("Time to initiate persona generation")
                .register(meterRegistry);
    }

    /**
     * Starts a persona generation workflow.
     * Creates a Persona entity in GENERATING state and publishes a task to the queue.
     */
    @Transactional
    public Long startPersonaGeneration(Long userId, PersonaGenerationRequest request) {
        try {
            return personaGenerationTimer.recordCallable(() -> executeStartPersonaGeneration(userId, request));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Long executeStartPersonaGeneration(Long userId, PersonaGenerationRequest request) {
        log.info("Starting persona generation for user {} with structured input", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ValidationException("User not found", ErrorCode.USER_NOT_FOUND.getCode()));

        // Serialize demographics and psychographics to JSON strings
        String demographicsJson;
        String psychographicsJson;
        try {
            demographicsJson = objectMapper.writeValueAsString(request.demographics());
            psychographicsJson = objectMapper.writeValueAsString(request.psychographics());
        } catch (Exception e) {
            throw new ValidationException(
                    "Failed to serialize persona generation request: " + e.getMessage(),
                    ErrorCode.VALIDATION_ERROR.getCode()
            );
        }

        // Store combined JSON as generation prompt for cache key purposes
        String generationPromptCache = demographicsJson + psychographicsJson;

        Persona persona = new Persona();
        persona.setUser(user);
        persona.setStatus(Persona.PersonaStatus.GENERATING);
        persona.setGenerationPrompt(generationPromptCache);
        persona.setName("Generating...");

        Persona savedPersona = personaRepository.save(persona);
        log.info("Created persona entity with id {}", savedPersona.getId());

        PersonaGenerationTask task = new PersonaGenerationTask(
                savedPersona.getId(),
                demographicsJson,
                psychographicsJson
        );
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                "persona.generation",
                task
        );
        log.info("Published persona generation task for persona {}", savedPersona.getId());

        personaGenerationInitiatedCounter.increment();
        return savedPersona.getId();
    }

    /**
     * Ищет или создаёт персоны по списку вариаций.
     *
     * Для каждой вариации:
     * 1. Ищет существующую активную персону по demographics
     * 2. Если найдена - переиспользует
     * 3. Если не найдена - создаёт новую и запускает генерацию через AI
     *
     * @param userId     ID пользователя
     * @param variations Список вариаций для поиска/создания
     * @return Список ID персон (существующих или новых)
     */
    @Transactional
    public List<Long> findOrCreatePersonas(Long userId, List<PersonaVariation> variations) {
        log.info("Finding or creating {} personas for user {}", variations.size(), userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ValidationException("User not found", ErrorCode.USER_NOT_FOUND.getCode()));

        List<Long> personaIds = new ArrayList<>();

        for (PersonaVariation variation : variations) {
            // Пытаемся найти существующую персону
            Optional<Persona> existingPersona = personaRepository.findByDemographics(
                    userId,
                    variation.gender(),
                    variation.age(),
                    variation.region(),
                    variation.incomeLevel()
            );

            if (existingPersona.isPresent()) {
                // Нашли - переиспользуем
                Long personaId = existingPersona.get().getId();
                personaIds.add(personaId);
                log.debug("Reusing existing persona {} for variation {}", personaId, variation);
            } else {
                // Не нашли - создаём новую
                Long newPersonaId = createPersonaFromVariation(user, variation);
                personaIds.add(newPersonaId);
                log.debug("Created new persona {} for variation {}", newPersonaId, variation);
            }
        }

        log.info("Found/created {} personas: {}", personaIds.size(), personaIds);
        return personaIds;
    }

    /**
     * Создаёт новую персону из вариации и запускает генерацию деталей через AI.
     *
     * @param user      Пользователь-владелец
     * @param variation Демографические параметры
     * @return ID созданной персоны
     */
    private Long createPersonaFromVariation(User user, PersonaVariation variation) {
        try {
            // Создаём demographics DTO для AI промпта
            PersonaDemographics demographics = new PersonaDemographics(
                    String.valueOf(variation.age()),  // age
                    variation.gender(),               // gender
                    variation.region(),               // location (используем region как location)
                    null,                             // occupation (будет заполнено AI)
                    variation.incomeLevel()           // income
            );

            // Генерируем базовые psychographics (AI дополнит детали)
            PersonaPsychographics psychographics = generateDefaultPsychographics(variation);

            // Сериализуем в JSON для AI
            String demographicsJson = objectMapper.writeValueAsString(demographics);
            String psychographicsJson = objectMapper.writeValueAsString(psychographics);

            // Создаём entity с demographics полями для БД-поиска
            Persona persona = new Persona();
            persona.setUser(user);
            persona.setStatus(Persona.PersonaStatus.GENERATING);
            persona.setName("Generating...");
            persona.setDemographicGender(variation.gender());
            persona.setAge(variation.age());
            persona.setRegion(variation.region());
            persona.setIncomeLevel(variation.incomeLevel());
            persona.setGenerationPrompt(demographicsJson + psychographicsJson);

            Persona savedPersona = personaRepository.save(persona);
            log.info("Created persona entity {} for variation {}", savedPersona.getId(), variation);

            // Публикуем задачу на генерацию деталей
            PersonaGenerationTask task = new PersonaGenerationTask(
                    savedPersona.getId(),
                    demographicsJson,
                    psychographicsJson
            );
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    "persona.generation",
                    task
            );
            log.info("Published generation task for persona {}", savedPersona.getId());

            personaGenerationInitiatedCounter.increment();
            return savedPersona.getId();
        } catch (Exception e) {
            throw new ValidationException(
                    "Failed to create persona from variation: " + e.getMessage(),
                    ErrorCode.VALIDATION_ERROR.getCode()
            );
        }
    }

    /**
     * Генерирует базовые psychographics на основе demographics.
     * AI потом дополнит детали в процессе генерации.
     */
    private PersonaPsychographics generateDefaultPsychographics(PersonaVariation variation) {
        // Базовые значения в зависимости от demographics
        String values = switch (variation.incomeLevel()) {
            case "low" -> "Value, practicality";
            case "medium" -> "Quality, reliability";
            case "high" -> "Premium quality, status";
            default -> "Balance of quality and value";
        };

        String lifestyle = switch (variation.region()) {
            case "moscow" -> "Urban, fast-paced";
            case "spb" -> "Cultural, cosmopolitan";
            case "regions" -> "Practical, traditional";
            default -> "Balanced lifestyle";
        };

        String painPoints = "Budget constraints, limited time, information overload";

        return new PersonaPsychographics(values, lifestyle, painPoints);
    }
}
