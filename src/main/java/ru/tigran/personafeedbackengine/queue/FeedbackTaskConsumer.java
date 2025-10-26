package ru.tigran.personafeedbackengine.queue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tigran.personafeedbackengine.config.RabbitMQConfig;
import ru.tigran.personafeedbackengine.dto.FeedbackGenerationTask;
import ru.tigran.personafeedbackengine.exception.ResourceNotFoundException;
import ru.tigran.personafeedbackengine.model.FeedbackResult;
import ru.tigran.personafeedbackengine.model.FeedbackSession;
import ru.tigran.personafeedbackengine.model.Persona;
import ru.tigran.personafeedbackengine.model.Product;
import ru.tigran.personafeedbackengine.repository.FeedbackResultRepository;
import ru.tigran.personafeedbackengine.repository.FeedbackSessionRepository;
import ru.tigran.personafeedbackengine.repository.PersonaRepository;
import ru.tigran.personafeedbackengine.repository.ProductRepository;
import ru.tigran.personafeedbackengine.service.AIGatewayService;

@Slf4j
@Service
public class FeedbackTaskConsumer {

    private final FeedbackResultRepository feedbackResultRepository;
    private final FeedbackSessionRepository feedbackSessionRepository;
    private final PersonaRepository personaRepository;
    private final ProductRepository productRepository;
    private final AIGatewayService aiGatewayService;

    public FeedbackTaskConsumer(
            FeedbackResultRepository feedbackResultRepository,
            FeedbackSessionRepository feedbackSessionRepository,
            PersonaRepository personaRepository,
            ProductRepository productRepository,
            AIGatewayService aiGatewayService
    ) {
        this.feedbackResultRepository = feedbackResultRepository;
        this.feedbackSessionRepository = feedbackSessionRepository;
        this.personaRepository = personaRepository;
        this.productRepository = productRepository;
        this.aiGatewayService = aiGatewayService;
    }

    /**
     * Consumes feedback generation tasks from the queue.
     * Generates feedback from a persona on a product and updates the FeedbackResult entity.
     * Checks if all results for the session are done and updates session status accordingly.
     */
    @RabbitListener(queues = RabbitMQConfig.FEEDBACK_GENERATION_QUEUE)
    @Transactional
    public void consumeFeedbackTask(FeedbackGenerationTask task) {
        log.info("Consuming feedback generation task for result {}", task.resultId());

        try {
            // Fetch FeedbackResult
            FeedbackResult result = feedbackResultRepository.findById(task.resultId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "FeedbackResult not found",
                            "FEEDBACK_RESULT_NOT_FOUND"
                    ));

            if (result.getStatus() == FeedbackResult.FeedbackResultStatus.COMPLETED) {
                log.info("FeedbackResult {} already completed, skipping", result.getId());
                return;
            }

            if (result.getStatus() == FeedbackResult.FeedbackResultStatus.FAILED) {
                log.warn("FeedbackResult {} previously failed, retrying", result.getId());
            }

            result.setStatus(FeedbackResult.FeedbackResultStatus.IN_PROGRESS);
            feedbackResultRepository.save(result);

            // Fetch Persona and Product
            Persona persona = personaRepository.findById(task.personaId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Persona not found",
                            "PERSONA_NOT_FOUND"
                    ));

            Product product = productRepository.findById(task.productId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found",
                            "PRODUCT_NOT_FOUND"
                    ));

            // Generate feedback via AI
            String feedback = aiGatewayService.generateFeedbackForProduct(
                    persona.getDetailedDescription(),
                    product.getDescription()
            );

            // Update FeedbackResult
            result.setFeedbackText(feedback);
            result.setStatus(FeedbackResult.FeedbackResultStatus.COMPLETED);
            feedbackResultRepository.save(result);
            log.info("Successfully generated and saved feedback result {}", result.getId());

            checkAndUpdateSessionCompletion(result.getFeedbackSession().getId());

        } catch (Exception e) {
            log.error("Error processing feedback generation task for result {}", task.resultId(), e);
            try {
                FeedbackResult result = feedbackResultRepository.findById(task.resultId()).orElse(null);
                if (result != null) {
                    result.setStatus(FeedbackResult.FeedbackResultStatus.FAILED);
                    feedbackResultRepository.save(result);
                    log.warn("Marked feedback result {} as FAILED", result.getId());

                    checkAndUpdateSessionCompletion(result.getFeedbackSession().getId());
                }
            } catch (Exception innerE) {
                log.error("Failed to mark feedback result as FAILED", innerE);
            }
        }
    }

    private void checkAndUpdateSessionCompletion(Long sessionId) {
        try {
            FeedbackSession session = feedbackSessionRepository.findByIdForUpdate(sessionId)
                    .orElse(null);

            if (session == null) {
                return;
            }

            long completedCount = feedbackResultRepository.countBySessionAndStatus(
                    sessionId,
                    FeedbackResult.FeedbackResultStatus.COMPLETED
            );
            long failedCount = feedbackResultRepository.countBySessionAndStatus(
                    sessionId,
                    FeedbackResult.FeedbackResultStatus.FAILED
            );
            long totalCount = feedbackResultRepository.findByFeedbackSessionId(sessionId).size();

            if (completedCount + failedCount >= totalCount) {
                int updated = feedbackSessionRepository.updateStatusIfNotAlready(
                        sessionId,
                        FeedbackSession.FeedbackSessionStatus.COMPLETED
                );
                if (updated > 0) {
                    log.info("Feedback session {} marked as COMPLETED", sessionId);
                }
            }
        } catch (Exception e) {
            log.error("Error updating feedback session completion status for session {}", sessionId, e);
        }
    }
}
