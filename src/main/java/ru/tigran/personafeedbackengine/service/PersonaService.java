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
        List<Long> createdPersonaIds = new ArrayList<>();

        // Create multiple personas based on the count
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
            createdPersonaIds.add(savedPersona.getId());
            log.info("Created persona entity {} ({}/{})", savedPersona.getId(), i + 1, personaCount);

            // Publish task to queue for AI generation
            PersonaGenerationTask task = new PersonaGenerationTask(
                    savedPersona.getId(),
                    buildDemographicsJson(request),
                    buildPsychographicsJson(request)
            );
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    "persona.generation",
                    task
            );
            log.info("Published persona generation task for persona {}", savedPersona.getId());

            personaGenerationInitiatedCounter.increment();
        }

        // Return the ID of the first created persona (or a job/batch ID in real scenario)
        // In production, you might return a batch ID linking all personas
        return createdPersonaIds.get(0);
    }

    /**
     * Calculates age group string from min and max age
     */
    private String calculateAgeGroup(Integer minAge, Integer maxAge) {
        if (minAge == null || maxAge == null) {
            return null;
        }
        int avgAge = (minAge + maxAge) / 2;
        return switch (avgAge) {
            case _ when avgAge < 25 -> "18-24";
            case _ when avgAge < 35 -> "25-34";
            case _ when avgAge < 45 -> "35-44";
            case _ when avgAge < 55 -> "45-54";
            case _ when avgAge < 65 -> "55-64";
            default -> "65+";
        };
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
