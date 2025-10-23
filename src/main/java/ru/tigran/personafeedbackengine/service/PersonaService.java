package ru.tigran.personafeedbackengine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tigran.personafeedbackengine.config.RabbitMQConfig;
import ru.tigran.personafeedbackengine.dto.PersonaGenerationRequest;
import ru.tigran.personafeedbackengine.dto.PersonaGenerationTask;
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
    private final int maxPromptLength;

    public PersonaService(
            PersonaRepository personaRepository,
            UserRepository userRepository,
            RabbitTemplate rabbitTemplate,
            @Value("${app.feedback.max-prompt-length}") int maxPromptLength
    ) {
        this.personaRepository = personaRepository;
        this.userRepository = userRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.maxPromptLength = maxPromptLength;
    }

    /**
     * Starts a persona generation workflow.
     * Creates a Persona entity in GENERATING state and publishes a task to the queue.
     */
    @Transactional
    public Long startPersonaGeneration(Long userId, PersonaGenerationRequest request) {
        log.info("Starting persona generation for user {}", userId);

        // Validate request
        if (request.prompt().length() > maxPromptLength) {
            throw new ValidationException(
                    "Prompt exceeds maximum length of " + maxPromptLength + " characters",
                    "INVALID_PROMPT_LENGTH"
            );
        }

        // Verify user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ValidationException("User not found", "USER_NOT_FOUND"));

        // Create Persona entity in GENERATING state
        Persona persona = new Persona();
        persona.setUser(user);
        persona.setStatus(Persona.PersonaStatus.GENERATING);
        persona.setGenerationPrompt(request.prompt());
        persona.setName("Generated Persona"); // Placeholder name

        Persona savedPersona = personaRepository.save(persona);
        log.info("Created persona entity with id {}", savedPersona.getId());

        // Publish task to queue
        PersonaGenerationTask task = new PersonaGenerationTask(savedPersona.getId(), request.prompt());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                "persona.generation",
                task
        );
        log.info("Published persona generation task for persona {}", savedPersona.getId());

        return savedPersona.getId();
    }
}
