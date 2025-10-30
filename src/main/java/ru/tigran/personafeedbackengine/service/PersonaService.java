package ru.tigran.personafeedbackengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tigran.personafeedbackengine.config.RabbitMQConfig;
import ru.tigran.personafeedbackengine.dto.*;
import ru.tigran.personafeedbackengine.exception.ErrorCode;
import ru.tigran.personafeedbackengine.exception.ValidationException;
import ru.tigran.personafeedbackengine.model.Persona;
import ru.tigran.personafeedbackengine.model.User;
import ru.tigran.personafeedbackengine.repository.PersonaRepository;
import ru.tigran.personafeedbackengine.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PersonaService {

    /**
     * Helper record for storing persona generation information
     * Used in two-phase approach to separate persona creation from task publishing
     */
    private record PersonaGenerationInfo(
            Long personaId,
            PersonaGenerationTask task,
            int variantNumber
    ) {}

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
        log.info("Starting persona generation for user {} with count: {}", userId, request.getCountOrDefault());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ValidationException("User not found", ErrorCode.USER_NOT_FOUND.getCode()));

        // Create a characteristics hash for fast searching and reuse
        String characteristicsJson;
        try {
            characteristicsJson = createCharacteristicsHash(request);
        } catch (Exception e) {
            throw new ValidationException(
                    "Failed to serialize characteristics: " + e.getMessage(),
                    ErrorCode.VALIDATION_ERROR.getCode()
            );
        }

        // Get the count of personas to generate (default 6)
        int personaCount = request.getCountOrDefault();
        List<Persona> createdPersonas = new ArrayList<>();

        // Step 1: Create ALL personas first (without publishing tasks yet)
        for (int i = 0; i < personaCount; i++) {
            Persona persona = new Persona();
            persona.setUser(user);
            persona.setStatus(Persona.PersonaStatus.GENERATING);
            persona.setName("Generating...");

            // Fill demographic fields from request
            persona.setCountry(request.country().getCode());
            persona.setCity(request.city());
            persona.setDemographicGender(request.gender().getValue());
            persona.setMinAge(request.minAge());
            persona.setMaxAge(request.maxAge());
            persona.setActivitySphere(request.activitySphere().getValue());
            persona.setProfession(request.profession());
            persona.setIncome(request.income());

            // Fill psychographic fields from request
            if (request.interests() != null) {
                try {
                    persona.setInterests(objectMapper.writeValueAsString(request.interests()));
                } catch (Exception e) {
                    log.warn("Could not serialize interests: {}", e.getMessage());
                }
            }
            persona.setAdditionalParams(request.additionalParams());

            // Calculate age group from min/max age
            String ageGroup = calculateAgeGroup(request.minAge(), request.maxAge());
            persona.setAgeGroup(ageGroup);

            // Store characteristics hash for fast search
            persona.setCharacteristicsHash(characteristicsJson);

            // Store generation prompt for caching purposes
            persona.setGenerationPrompt(characteristicsJson);

            // Save persona to database
            Persona savedPersona = personaRepository.save(persona);
            createdPersonas.add(savedPersona);
            log.info("Created persona entity {} ({}/{})", savedPersona.getId(), i + 1, personaCount);
        }

        // Step 2: Flush to ensure all personas are persisted before publishing tasks
        // This prevents race conditions where RabbitMQ consumer tries to fetch persona before it's committed
        personaRepository.flush();
        log.info("Flushed {} personas to database before publishing tasks", createdPersonas.size());

        // Step 3: Now publish ALL generation tasks to queue
        for (int i = 0; i < createdPersonas.size(); i++) {
            Persona persona = createdPersonas.get(i);

            // Build demographics and psychographics with variant number for diversity
            String demographicsJson = buildDemographicsJson(request);
            String psychographicsJson = buildPsychographicsJsonWithVariant(request, i + 1);

            // Publish task to queue for AI generation
            PersonaGenerationTask task = new PersonaGenerationTask(
                    persona.getId(),
                    demographicsJson,
                    psychographicsJson
            );
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    "persona.generation",
                    task
            );
            log.info("Published persona generation task for persona {} (variant {}/{})", persona.getId(), i + 1, createdPersonas.size());

            personaGenerationInitiatedCounter.increment();
        }

        log.info("Successfully created and published {} personas for user {}", createdPersonas.size(), userId);

        // Return the ID of the first created persona (or a job/batch ID in real scenario)
        // In production, you might return a batch ID linking all personas
        return createdPersonas.get(0).getId();
    }

    /**
     * Calculates age group string from min and max age
     */
    private String calculateAgeGroup(Integer minAge, Integer maxAge) {
        if (minAge == null || maxAge == null) {
            return null;
        }
        int avgAge = (minAge + maxAge) / 2;

        if (avgAge < 25) {
            return "18-24";
        } else if (avgAge < 35) {
            return "25-34";
        } else if (avgAge < 45) {
            return "35-44";
        } else if (avgAge < 55) {
            return "45-54";
        } else if (avgAge < 65) {
            return "55-64";
        } else {
            return "65+";
        }
    }

    /**
     * Builds demographics JSON from new request structure
     */
    private String buildDemographicsJson(PersonaGenerationRequest request) {
        try {
            PersonaDemographics demographics = new PersonaDemographics(
                    request.minAge() + "-" + request.maxAge(),  // age range
                    request.gender().getValue(),
                    request.city() + ", " + request.country().getDisplayName(),
                    request.profession(),
                    request.income()
            );
            return objectMapper.writeValueAsString(demographics);
        } catch (Exception e) {
            log.warn("Could not build demographics JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Builds psychographics JSON from new request structure
     */
    private String buildPsychographicsJson(PersonaGenerationRequest request) {
        try {
            String interests = request.interests() != null ? String.join(", ", request.interests()) : "";
            PersonaPsychographics psychographics = new PersonaPsychographics(
                    request.activitySphere().getDisplayName() + (interests.isEmpty() ? "" : ", " + interests),
                    request.additionalParams() != null ? request.additionalParams() : "",
                    ""  // painPoints will be determined by AI
            );
            return objectMapper.writeValueAsString(psychographics);
        } catch (Exception e) {
            log.warn("Could not build psychographics JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Builds psychographics JSON with variant number for diversity.
     * Includes variant_number to encourage AI to generate different personas for batch requests.
     *
     * @param request The persona generation request
     * @param variantNumber The variant number (1-indexed)
     * @return JSON string with psychographics including variant number
     */
    private String buildPsychographicsJsonWithVariant(PersonaGenerationRequest request, int variantNumber) {
        try {
            String interests = request.interests() != null ? String.join(", ", request.interests()) : "";
            Map<String, Object> psychographicsMap = new LinkedHashMap<>();
            psychographicsMap.put("values", request.activitySphere().getDisplayName() + (interests.isEmpty() ? "" : ", " + interests));
            psychographicsMap.put("lifestyle", request.additionalParams() != null ? request.additionalParams() : "");
            psychographicsMap.put("pain_points", "");  // AI will determine
            psychographicsMap.put("variant_number", variantNumber);  // For diversity hint
            return objectMapper.writeValueAsString(psychographicsMap);
        } catch (Exception e) {
            log.warn("Could not build psychographics JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Creates a JSON hash of all characteristics for caching and reuse
     */
    private String createCharacteristicsHash(PersonaGenerationRequest request) throws Exception {
        Map<String, Object> characteristics = new LinkedHashMap<>();
        characteristics.put("country", request.country().getCode());
        characteristics.put("city", request.city());
        characteristics.put("gender", request.gender().getValue());
        characteristics.put("minAge", request.minAge());
        characteristics.put("maxAge", request.maxAge());
        characteristics.put("activitySphere", request.activitySphere().getValue());
        characteristics.put("profession", request.profession());
        characteristics.put("income", request.income());
        characteristics.put("interests", request.interests());
        characteristics.put("additionalParams", request.additionalParams());
        return objectMapper.writeValueAsString(characteristics);
    }

    /**
     * Searches for existing personas by characteristics
     */
    @Transactional(readOnly = true)
    public List<PersonaResponse> searchPersonas(Long userId, String country, String city, String gender, String activitySphere) {
        log.info("Searching personas for user {} with filters: country={}, city={}, gender={}, activitySphere={}",
                userId, country, city, gender, activitySphere);

        // Build dynamic query based on provided filters
        List<Persona> personas = personaRepository.findAll()
                .stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .filter(p -> !p.getDeleted())
                .filter(p -> p.getStatus() == Persona.PersonaStatus.ACTIVE)
                .filter(p -> country == null || country.isEmpty() || p.getCountry().equalsIgnoreCase(country))
                .filter(p -> city == null || city.isEmpty() || p.getCity().equalsIgnoreCase(city))
                .filter(p -> gender == null || gender.isEmpty() || p.getDemographicGender().equalsIgnoreCase(gender))
                .filter(p -> activitySphere == null || activitySphere.isEmpty() || p.getActivitySphere().equalsIgnoreCase(activitySphere))
                .toList();

        return personas.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Gets a specific persona by ID with full details
     */
    @Transactional(readOnly = true)
    public PersonaResponse getPersonaResponse(Long userId, Long personaId) {
        log.info("Getting persona {} for user {}", personaId, userId);

        Persona persona = personaRepository.findById(personaId)
                .orElseThrow(() -> new ValidationException("Persona not found", ErrorCode.PERSONA_NOT_FOUND.getCode()));

        // Check ownership
        if (!persona.getUser().getId().equals(userId)) {
            throw new ValidationException("Access denied: persona belongs to another user", "ACCESS_DENIED");
        }

        return convertToResponse(persona);
    }

    /**
     * Converts Persona entity to PersonaResponse DTO
     */
    private PersonaResponse convertToResponse(Persona persona) {
        List<String> interests = null;
        if (persona.getInterests() != null && !persona.getInterests().isEmpty()) {
            try {
                interests = Arrays.asList(objectMapper.readValue(persona.getInterests(), String[].class));
            } catch (Exception e) {
                log.warn("Could not deserialize interests for persona {}: {}", persona.getId(), e.getMessage());
            }
        }

        return new PersonaResponse(
                persona.getId(),
                persona.getStatus().toString(),
                persona.getName(),
                persona.getDetailedDescription(),
                persona.getProductAttitudes(),
                persona.getDemographicGender(),
                persona.getCountry(),
                persona.getCity(),
                persona.getMinAge(),
                persona.getMaxAge(),
                persona.getActivitySphere(),
                persona.getProfession(),
                persona.getIncome(),
                interests,
                persona.getAdditionalParams(),
                persona.getAgeGroup(),
                persona.getRace(),
                persona.getAvatarUrl(),
                persona.getCreatedAt()
        );
    }

    /**
     * Ищет или создаёт персоны по списку вариаций.
     *
     * Для каждой вариации:
     * 1. Ищет существующую активную персону по demographics
     * 2. Если найдена - переиспользует
     * 3. Если не найдена - создаёт новую и запускает генерацию через AI
     *
     * Uses two-phase approach to prevent race conditions:
     * - Phase 1: Find existing or create all new personas and persist to DB
     * - Phase 2: Flush all to ensure database consistency
     * - Phase 3: Publish generation tasks for newly created personas
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
        List<PersonaGenerationInfo> newPersonasToPublish = new ArrayList<>();

        // Phase 1: Find existing or create new personas
        for (int index = 0; index < variations.size(); index++) {
            PersonaVariation variation = variations.get(index);

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
                // Не нашли - создаём новую (без публикации task)
                PersonaGenerationInfo generationInfo = createPersonaFromVariationWithoutPublishing(user, variation, index + 1);
                personaIds.add(generationInfo.personaId());
                newPersonasToPublish.add(generationInfo);
                log.debug("Created new persona {} for variation {}", generationInfo.personaId(), variation);
            }
        }

        // Phase 2: Flush all new personas to database to ensure they are persisted
        if (!newPersonasToPublish.isEmpty()) {
            personaRepository.flush();
            log.info("Flushed {} new personas to database before publishing tasks", newPersonasToPublish.size());
        }

        // Phase 3: Publish generation tasks for newly created personas
        for (PersonaGenerationInfo generationInfo : newPersonasToPublish) {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    "persona.generation",
                    generationInfo.task()
            );
            log.info("Published generation task for persona {} (variant {}/{})",
                    generationInfo.personaId(), generationInfo.variantNumber(), newPersonasToPublish.size());
            personaGenerationInitiatedCounter.increment();
        }

        log.info("Found/created {} personas ({} new, {} existing): {}",
                personaIds.size(), newPersonasToPublish.size(), personaIds.size() - newPersonasToPublish.size(), personaIds);
        return personaIds;
    }

    /**
     * Создаёт новую персону из вариации и запускает генерацию деталей через AI.
     * (Существует для обратной совместимости, вызывает основной метод)
     *
     * @param user      Пользователь-владелец
     * @param variation Демографические параметры
     * @return ID созданной персоны
     */
    private Long createPersonaFromVariation(User user, PersonaVariation variation) {
        PersonaGenerationInfo info = createPersonaFromVariationWithoutPublishing(user, variation, 1);

        // Publish the task immediately (for backward compatibility with old code)
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                "persona.generation",
                info.task()
        );
        log.info("Published generation task for persona {}", info.personaId());
        personaGenerationInitiatedCounter.increment();

        return info.personaId();
    }

    /**
     * Создаёт новую персону из вариации БЕЗ публикации task.
     * Используется в двухфазном подходе findOrCreatePersonas для предотвращения race conditions.
     *
     * @param user           Пользователь-владелец
     * @param variation      Демографические параметры
     * @param variantNumber  Номер варианта (для diversity)
     * @return PersonaGenerationInfo с персоной и task (но task ещё не опубликован)
     */
    private PersonaGenerationInfo createPersonaFromVariationWithoutPublishing(
            User user,
            PersonaVariation variation,
            int variantNumber
    ) {
        try {
            // Создаём demographics DTO для AI промпта
            PersonaDemographics demographics = new PersonaDemographics(
                    String.valueOf(variation.age()),  // age
                    variation.gender(),               // gender
                    variation.region(),               // location (используем region как location)
                    null,                             // occupation (будет заполнено AI)
                    variation.incomeLevel()           // income
            );

            // Генерируем базовые psychographics с вариант номером (AI дополнит детали)
            PersonaPsychographics psychographics = generateDefaultPsychographics(variation);
            String psychographicsJson = buildPsychographicsFromPsychographicsObjectWithVariant(psychographics, variantNumber);

            // Сериализуем в JSON для AI
            String demographicsJson = objectMapper.writeValueAsString(demographics);

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
            log.info("Created persona entity {} for variation {} (variant {})", savedPersona.getId(), variation, variantNumber);

            // Подготавливаем task (но НЕ публикуем его)
            PersonaGenerationTask task = new PersonaGenerationTask(
                    savedPersona.getId(),
                    demographicsJson,
                    psychographicsJson
            );

            return new PersonaGenerationInfo(savedPersona.getId(), task, variantNumber);
        } catch (Exception e) {
            throw new ValidationException(
                    "Failed to create persona from variation: " + e.getMessage(),
                    ErrorCode.VALIDATION_ERROR.getCode()
            );
        }
    }

    /**
     * Конвертирует PersonaPsychographics объект в JSON строку с добавлением варианта номера.
     */
    private String buildPsychographicsFromPsychographicsObjectWithVariant(
            PersonaPsychographics psychographics,
            int variantNumber
    ) {
        try {
            Map<String, Object> psychographicsMap = new LinkedHashMap<>();
            psychographicsMap.put("values", psychographics.values());
            psychographicsMap.put("lifestyle", psychographics.lifestyle());
            psychographicsMap.put("pain_points", psychographics.painPoints());
            psychographicsMap.put("variant_number", variantNumber);  // For diversity hint
            return objectMapper.writeValueAsString(psychographicsMap);
        } catch (Exception e) {
            log.warn("Could not build psychographics JSON: {}", e.getMessage());
            return "{}";
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
