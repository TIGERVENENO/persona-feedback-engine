package ru.tigran.personafeedbackengine.service;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tigran.personafeedbackengine.dto.FeedbackGenerationTask;
import ru.tigran.personafeedbackengine.dto.SessionStatusInfo;
import ru.tigran.personafeedbackengine.exception.ResourceNotFoundException;
import ru.tigran.personafeedbackengine.model.FeedbackResult;
import ru.tigran.personafeedbackengine.model.FeedbackSession;
import ru.tigran.personafeedbackengine.model.Persona;
import ru.tigran.personafeedbackengine.model.Product;
import ru.tigran.personafeedbackengine.repository.FeedbackResultRepository;
import ru.tigran.personafeedbackengine.repository.FeedbackSessionRepository;
import ru.tigran.personafeedbackengine.repository.PersonaRepository;
import ru.tigran.personafeedbackengine.repository.ProductRepository;

import java.util.concurrent.TimeUnit;

/**
 * Service for feedback generation business logic.
 * Handles AI-driven feedback generation, result updates, and session completion.
 * Extracted from FeedbackTaskConsumer to follow Single Responsibility Principle.
 */
@Slf4j
@Service
public class FeedbackGenerationService {

    private final FeedbackResultRepository feedbackResultRepository;
    private final FeedbackSessionRepository feedbackSessionRepository;
    private final PersonaRepository personaRepository;
    private final ProductRepository productRepository;
    private final AIGatewayService aiGatewayService;
    private final RedissonClient redissonClient;

    public FeedbackGenerationService(
            FeedbackResultRepository feedbackResultRepository,
            FeedbackSessionRepository feedbackSessionRepository,
            PersonaRepository personaRepository,
            ProductRepository productRepository,
            AIGatewayService aiGatewayService,
            RedissonClient redissonClient
    ) {
        this.feedbackResultRepository = feedbackResultRepository;
        this.feedbackSessionRepository = feedbackSessionRepository;
        this.personaRepository = personaRepository;
        this.productRepository = productRepository;
        this.aiGatewayService = aiGatewayService;
        this.redissonClient = redissonClient;
    }

    /**
     * Processes feedback generation task.
     * Generates feedback from a persona on a product and updates the FeedbackResult entity.
     * After completion, checks if all results for the session are done and updates session status.
     *
     * @param task Feedback generation task with result ID, product ID, and persona ID
     * @throws ResourceNotFoundException if FeedbackResult, Persona, or Product not found
     */
    @Transactional
    public void generateFeedback(FeedbackGenerationTask task) {
        log.debug("Starting feedback generation for result {}", task.resultId());

        // Fetch FeedbackResult
        FeedbackResult result = feedbackResultRepository.findById(task.resultId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FeedbackResult not found",
                        "FEEDBACK_RESULT_NOT_FOUND"
                ));

        // Check idempotency
        if (result.getStatus() == FeedbackResult.FeedbackResultStatus.COMPLETED) {
            log.info("FeedbackResult {} already completed, skipping", result.getId());
            return;
        }

        if (result.getStatus() == FeedbackResult.FeedbackResultStatus.FAILED) {
            log.warn("FeedbackResult {} previously failed, retrying", result.getId());
        }

        // Mark result as in progress
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

        // Check and update session completion status
        checkAndUpdateSessionCompletion(result.getFeedbackSession().getId());
    }

    /**
     * Checks if all feedback results for a session are done and updates session status.
     * Uses distributed locking to ensure atomic updates across multiple instances.
     *
     * @param sessionId ID of the feedback session
     */
    private void checkAndUpdateSessionCompletion(Long sessionId) {
        String lockKey = "feedback-session-lock:" + sessionId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // Try to acquire lock with 10 second timeout
            boolean locked = lock.tryLock(10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("Could not acquire lock for feedback session {}", sessionId);
                return;
            }

            try {
                FeedbackSession session = feedbackSessionRepository.findByIdForUpdate(sessionId)
                        .orElse(null);

                if (session == null) {
                    return;
                }

                // Get current session status
                SessionStatusInfo statusInfo = feedbackResultRepository.getSessionStatus(sessionId);

                // If all results are done (completed or failed), mark session as completed
                if (statusInfo.completed() + statusInfo.failed() >= statusInfo.total()) {
                    int updated = feedbackSessionRepository.updateStatusIfNotAlready(
                            sessionId,
                            FeedbackSession.FeedbackSessionStatus.COMPLETED
                    );
                    if (updated > 0) {
                        log.info("Feedback session {} marked as COMPLETED", sessionId);
                    }
                }
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring lock for feedback session {}", sessionId, e);
        } catch (Exception e) {
            log.error("Error updating feedback session completion status for session {}", sessionId, e);
        }
    }
}
