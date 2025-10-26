package ru.tigran.personafeedbackengine.queue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import ru.tigran.personafeedbackengine.config.RabbitMQConfig;
import ru.tigran.personafeedbackengine.dto.PersonaGenerationTask;
import ru.tigran.personafeedbackengine.model.Persona;
import ru.tigran.personafeedbackengine.repository.PersonaRepository;
import ru.tigran.personafeedbackengine.service.PersonaGenerationService;

@Slf4j
@Service
public class PersonaTaskConsumer {

    private final PersonaGenerationService personaGenerationService;
    private final PersonaRepository personaRepository;

    public PersonaTaskConsumer(
            PersonaGenerationService personaGenerationService,
            PersonaRepository personaRepository
    ) {
        this.personaGenerationService = personaGenerationService;
        this.personaRepository = personaRepository;
    }

    /**
     * Consumes persona generation tasks from the queue.
     * Delegates to PersonaGenerationService for business logic.
     */
    @RabbitListener(queues = RabbitMQConfig.PERSONA_GENERATION_QUEUE)
    public void consumePersonaTask(PersonaGenerationTask task) {
        log.info("Consuming persona generation task for persona {}", task.personaId());

        try {
            personaGenerationService.generatePersona(task);
        } catch (Exception e) {
            log.error("Error processing persona generation task for persona {}", task.personaId(), e);

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
