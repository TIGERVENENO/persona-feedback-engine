package ru.tigran.personafeedbackengine.queue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import ru.tigran.personafeedbackengine.config.RabbitMQConfig;
import ru.tigran.personafeedbackengine.dto.FeedbackGenerationTask;
import ru.tigran.personafeedbackengine.model.FeedbackResult;
import ru.tigran.personafeedbackengine.repository.FeedbackResultRepository;
import ru.tigran.personafeedbackengine.service.FeedbackGenerationService;

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
     * Consumes feedback generation tasks from the queue.
     * Delegates to FeedbackGenerationService for business logic.
     */
    @RabbitListener(queues = RabbitMQConfig.FEEDBACK_GENERATION_QUEUE)
    public void consumeFeedbackTask(FeedbackGenerationTask task) {
        log.info("Consuming feedback generation task for result {}", task.resultId());

        try {
            feedbackGenerationService.generateFeedback(task);
        } catch (Exception e) {
            log.error("Error processing feedback generation task for result {}", task.resultId(), e);
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
        }
    }
}
