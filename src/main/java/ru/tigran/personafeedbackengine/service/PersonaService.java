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
import ru.tigran.personafeedbackengine.dto.PersonaGenerationRequest;
import ru.tigran.personafeedbackengine.dto.PersonaGenerationTask;
import ru.tigran.personafeedbackengine.exception.ErrorCode;
import ru.tigran.personafeedbackengine.exception.ValidationException;
import ru.tigran.personafeedbackengine.model.Persona;
import ru.tigran.personafeedbackengine.model.User;
import ru.tigran.personafeedbackengine.repository.PersonaRepository;
import ru.tigran.personafeedbackengine.repository.UserRepository;

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
}
