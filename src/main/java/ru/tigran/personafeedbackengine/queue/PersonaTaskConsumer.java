package ru.tigran.personafeedbackengine.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tigran.personafeedbackengine.config.RabbitMQConfig;
import ru.tigran.personafeedbackengine.dto.PersonaGenerationTask;
import ru.tigran.personafeedbackengine.exception.ResourceNotFoundException;
import ru.tigran.personafeedbackengine.model.Persona;
import ru.tigran.personafeedbackengine.repository.PersonaRepository;
import ru.tigran.personafeedbackengine.service.AIGatewayService;

@Slf4j
@Service
public class PersonaTaskConsumer {

    private final PersonaRepository personaRepository;
    private final AIGatewayService aiGatewayService;
    private final ObjectMapper objectMapper;

    public PersonaTaskConsumer(
            PersonaRepository personaRepository,
            AIGatewayService aiGatewayService,
            ObjectMapper objectMapper
    ) {
        this.personaRepository = personaRepository;
        this.aiGatewayService = aiGatewayService;
        this.objectMapper = objectMapper;
    }

    /**
     * Consumes persona generation tasks from the queue.
     * Generates persona details via AI and updates the Persona entity.
     */
    @RabbitListener(queues = RabbitMQConfig.PERSONA_GENERATION_QUEUE)
    @Transactional
    public void consumePersonaTask(PersonaGenerationTask task) {
        log.info("Consuming persona generation task for persona {}", task.personaId());

        try {
            // Fetch Persona entity
            Persona persona = personaRepository.findById(task.personaId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Persona not found",
                            "PERSONA_NOT_FOUND"
                    ));

            // Generate persona details via AI (cached by prompt)
            String personaDetailsJson = aiGatewayService.generatePersonaDetails(task.userPrompt());

            // Parse the JSON response
            JsonNode details = objectMapper.readTree(personaDetailsJson);

            // Update Persona entity with generated details
            persona.setName(details.get("nm").asText());
            persona.setDetailedDescription(details.get("dd").asText());
            persona.setGender(details.get("g").asText());
            persona.setAgeGroup(details.get("ag").asText());
            persona.setRace(details.get("r").asText());
            persona.setAvatarUrl(details.get("au").asText());
            persona.setStatus(Persona.PersonaStatus.ACTIVE);

            personaRepository.save(persona);
            log.info("Successfully generated and saved persona {}", persona.getId());

        } catch (Exception e) {
            log.error("Error processing persona generation task for persona {}", task.personaId(), e);
            // Mark persona as FAILED
            try {
                Persona persona = personaRepository.findById(task.personaId()).orElse(null);
                if (persona != null) {
                    persona.setStatus(Persona.PersonaStatus.FAILED);
                    personaRepository.save(persona);
                    log.warn("Marked persona {} as FAILED", persona.getId());
                }
            } catch (Exception innerE) {
                log.error("Failed to mark persona as FAILED", innerE);
            }
        }
    }
}
