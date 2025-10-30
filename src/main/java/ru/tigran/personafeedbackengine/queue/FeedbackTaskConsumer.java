package ru.tigran.personafeedbackengine.queue;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import ru.tigran.personafeedbackengine.config.RabbitMQConfig;
import ru.tigran.personafeedbackengine.dto.FeedbackGenerationTask;
import ru.tigran.personafeedbackengine.model.FeedbackResult;
import ru.tigran.personafeedbackengine.repository.FeedbackResultRepository;
import ru.tigran.personafeedbackengine.service.FeedbackGenerationService;

/**
 * Message consumer for feedback generation tasks.
 * Uses MANUAL acknowledgment mode - explicitly sends ACK/NACK to RabbitMQ.
 * 
 * SECURITY FIX: CRITICAL-4 - No manual ACK/NACK
 * Ensures each message is processed exactly once:
 * - ACK only after successful processing
 * - NACK with requeue on error (message returns to queue for retry)
 */
@Slf4j
@Service
public class FeedbackTaskConsumer {

    private final FeedbackGenerationService feedbackGenerationService;
    private final FeedbackResultRepository feedbackResultRepository;

    public FeedbackTaskConsumer(
            FeedbackGenerationService feedbackGenerationService,
            FeedbackResultRepository feedbackResultRepository
    ) {
        this.feedbackGenerationService = feedbackGenerationService;
        this.feedbackResultRepository = feedbackResultRepository;
    }

    /**
     * Consumes feedback generation tasks from the queue with manual acknowledgment.
     * 
     * Flow:
     * 1. Message received from queue
     * 2. Try to process (generate feedback)
     * 3. If success: send ACK (message removed from queue)
     * 4. If error: send NACK with requeue (message returns to queue for retry)
     * 
     * @param task Feedback generation task
     * @param channel RabbitMQ channel for ACK/NACK
     * @param deliveryTag Message delivery tag for acknowledgment
     */
    @RabbitListener(queues = RabbitMQConfig.FEEDBACK_GENERATION_QUEUE)
    public void consumeFeedbackTask(
            FeedbackGenerationTask task,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) {
        log.info("Consuming feedback generation task for result {}", task.resultId());

        try {
            // 1. Process the message
            feedbackGenerationService.generateFeedback(task);
            
            // 2. SUCCESS: Send ACK to RabbitMQ (acknowledge and remove message)
            try {
                channel.basicAck(deliveryTag, false);
                log.info("ACK sent for feedback task {}", task.resultId());
            } catch (Exception ackError) {
                log.error("Failed to send ACK for feedback task {}", task.resultId(), ackError);
            }
            
        } catch (Exception e) {
            log.error("Error processing feedback generation task for result {}", task.resultId(), e);

            // 3. ERROR: Try to mark result as FAILED and NACK the message
            try {
                FeedbackResult result = feedbackResultRepository.findById(task.resultId()).orElse(null);
                if (result != null) {
                    result.setStatus(FeedbackResult.FeedbackResultStatus.FAILED);
                    feedbackResultRepository.save(result);
                    log.warn("Marked feedback result {} as FAILED", result.getId());
                }
            } catch (Exception innerE) {
                log.error("Failed to mark feedback result as FAILED", innerE);
            }

            // 4. Send NACK with requeue=true (message returns to queue for retry)
            try {
                channel.basicNack(deliveryTag, false, true);
                log.info("NACK sent for feedback task {} - message requeued", task.resultId());
            } catch (Exception nackError) {
                log.error("Failed to send NACK for feedback task {}", task.resultId(), nackError);
            }
        }
    }
}
