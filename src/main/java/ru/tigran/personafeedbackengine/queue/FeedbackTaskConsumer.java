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

            // Check if all results for the session are completed
            FeedbackSession session = result.getFeedbackSession();
            long completedCount = feedbackResultRepository.countByFeedbackSessionIdAndStatus(
                    session.getId(),
                    FeedbackResult.FeedbackResultStatus.COMPLETED.name()
            );
            long totalCount = feedbackResultRepository.findByFeedbackSessionId(session.getId()).size();

            if (completedCount == totalCount) {
                session.setStatus(FeedbackSession.FeedbackSessionStatus.COMPLETED);
                feedbackSessionRepository.save(session);
                log.info("Feedback session {} completed", session.getId());
            }

        } catch (Exception e) {
            log.error("Error processing feedback generation task for result {}", task.resultId(), e);
            // Mark result as FAILED
            try {
                FeedbackResult result = feedbackResultRepository.findById(task.resultId()).orElse(null);
                if (result != null) {
                    result.setStatus(FeedbackResult.FeedbackResultStatus.FAILED);
                    feedbackResultRepository.save(result);

                    // If all results are done (either completed or failed), mark session as completed or failed
                    FeedbackSession session = result.getFeedbackSession();
                    long failedCount = feedbackResultRepository.countByFeedbackSessionIdAndStatus(
                            session.getId(),
                            FeedbackResult.FeedbackResultStatus.FAILED.name()
                    );
                    long totalCount = feedbackResultRepository.findByFeedbackSessionId(session.getId()).size();

                    if (failedCount + countCompletedResults(session.getId()) >= totalCount) {
                        session.setStatus(FeedbackSession.FeedbackSessionStatus.COMPLETED);
                        feedbackSessionRepository.save(session);
                    }

                    log.warn("Marked feedback result {} as FAILED", result.getId());
                }
            } catch (Exception innerE) {
                log.error("Failed to mark feedback result as FAILED", innerE);
            }
        }
    }

    private long countCompletedResults(Long sessionId) {
        return feedbackResultRepository.countByFeedbackSessionIdAndStatus(
                sessionId,
                FeedbackResult.FeedbackResultStatus.COMPLETED.name()
        );
    }
}
