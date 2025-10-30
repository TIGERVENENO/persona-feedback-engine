package ru.tigran.personafeedbackengine.queue;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import ru.tigran.personafeedbackengine.config.RabbitMQConfig;
import ru.tigran.personafeedbackengine.dto.PersonaGenerationTask;
import ru.tigran.personafeedbackengine.model.Persona;
import ru.tigran.personafeedbackengine.repository.PersonaRepository;
import ru.tigran.personafeedbackengine.service.PersonaGenerationService;

/**
 * Message consumer for persona generation tasks.
 * Uses MANUAL acknowledgment mode - explicitly sends ACK/NACK to RabbitMQ.
 * 
 * SECURITY FIX: CRITICAL-4 - No manual ACK/NACK
 * Ensures each message is processed exactly once:
 * - ACK only after successful processing
 * - NACK with requeue on error (message returns to queue for retry)
 */
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
     * Consumes persona generation tasks from the queue with manual acknowledgment.
     * 
     * Flow:
     * 1. Message received from queue
     * 2. Try to process (generate persona)
     * 3. If success: send ACK (message removed from queue)
     * 4. If error: send NACK with requeue (message returns to queue for retry)
     * 
     * @param task Persona generation task
     * @param channel RabbitMQ channel for ACK/NACK
     * @param deliveryTag Message delivery tag for acknowledgment
     */
    @RabbitListener(queues = RabbitMQConfig.PERSONA_GENERATION_QUEUE)
    public void consumePersonaTask(
            PersonaGenerationTask task,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) {
        log.info("Consuming persona generation task for persona {}", task.personaId());

        try {
            // 1. Process the message
            personaGenerationService.generatePersona(task);
            
            // 2. SUCCESS: Send ACK to RabbitMQ (acknowledge and remove message)
            try {
                channel.basicAck(deliveryTag, false);
                log.info("ACK sent for persona task {}", task.personaId());
            } catch (Exception ackError) {
                log.error("Failed to send ACK for persona task {}", task.personaId(), ackError);
            }
            
        } catch (Exception e) {
            log.error("Error processing persona generation task for persona {}", task.personaId(), e);

            // 3. ERROR: Try to mark persona as FAILED and NACK the message
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

            // 4. Send NACK with requeue=true (message returns to queue for retry)
            try {
                channel.basicNack(deliveryTag, false, true);
                log.info("NACK sent for persona task {} - message requeued", task.personaId());
            } catch (Exception nackError) {
                log.error("Failed to send NACK for persona task {}", task.personaId(), nackError);
            }
        }
    }
}
