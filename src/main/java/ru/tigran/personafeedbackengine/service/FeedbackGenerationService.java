package ru.tigran.personafeedbackengine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tigran.personafeedbackengine.dto.FeedbackGenerationTask;
import ru.tigran.personafeedbackengine.dto.SessionStatusInfo;
import ru.tigran.personafeedbackengine.exception.AIGatewayException;
import ru.tigran.personafeedbackengine.exception.ErrorCode;
import ru.tigran.personafeedbackengine.exception.ResourceNotFoundException;
import ru.tigran.personafeedbackengine.model.FeedbackResult;
import ru.tigran.personafeedbackengine.model.FeedbackSession;
import ru.tigran.personafeedbackengine.model.Persona;
import ru.tigran.personafeedbackengine.model.Product;
import ru.tigran.personafeedbackengine.repository.FeedbackResultRepository;
import ru.tigran.personafeedbackengine.repository.FeedbackSessionRepository;
import ru.tigran.personafeedbackengine.repository.PersonaRepository;
import ru.tigran.personafeedbackengine.repository.ProductRepository;

import java.util.ArrayList;
import java.util.List;
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
    private final ObjectMapper objectMapper;

    public FeedbackGenerationService(
            FeedbackResultRepository feedbackResultRepository,
            FeedbackSessionRepository feedbackSessionRepository,
            PersonaRepository personaRepository,
            ProductRepository productRepository,
            AIGatewayService aiGatewayService,
            RedissonClient redissonClient,
            ObjectMapper objectMapper
    ) {
        this.feedbackResultRepository = feedbackResultRepository;
        this.feedbackSessionRepository = feedbackSessionRepository;
        this.personaRepository = personaRepository;
        this.productRepository = productRepository;
        this.aiGatewayService = aiGatewayService;
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
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
                        ErrorCode.FEEDBACK_RESULT_NOT_FOUND.getCode()
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
                        ErrorCode.PERSONA_NOT_FOUND.getCode()
                ));

        Product product = productRepository.findById(task.productId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found",
                        ErrorCode.PRODUCT_NOT_FOUND.getCode()
                ));

        // Generate feedback via AI with detailed product and persona information
        String feedbackJson = aiGatewayService.generateFeedbackForProduct(
                persona.getDetailedDescription(),  // detailed_bio
                persona.getProductAttitudes(),     // product_attitudes
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory(),
                product.getKeyFeatures(),
                task.language()
        );

        // Parse feedback JSON response
        JsonNode feedbackData = parseFeedbackResponse(feedbackJson);
        validateFeedbackResponse(feedbackData);

        // Extract fields from JSON
        String feedbackText = feedbackData.get("feedback").asText();
        Integer purchaseIntent = feedbackData.get("purchase_intent").asInt();
        List<String> keyConcerns = extractKeyConcerns(feedbackData.get("key_concerns"));

        // Update FeedbackResult with all fields
        result.setFeedbackText(feedbackText);
        result.setPurchaseIntent(purchaseIntent);
        result.setKeyConcerns(keyConcerns);
        result.setStatus(FeedbackResult.FeedbackResultStatus.COMPLETED);
        feedbackResultRepository.save(result);
        log.info("Successfully generated and saved feedback result {}", result.getId());

        // Check and update session completion status
        checkAndUpdateSessionCompletion(result.getFeedbackSession().getId());
    }

    /**
     * Parses feedback generation response from AI.
     *
     * @param feedbackJson JSON string from AI gateway
     * @return JsonNode with parsed feedback data
     * @throws AIGatewayException if JSON parsing fails
     */
    private JsonNode parseFeedbackResponse(String feedbackJson) {
        try {
            return objectMapper.readTree(feedbackJson);
        } catch (Exception e) {
            String message = "Failed to parse feedback generation response: " + e.getMessage();
            log.error(message);
            throw new AIGatewayException(message, ErrorCode.INVALID_JSON_RESPONSE.getCode());
        }
    }

    /**
     * Validates that all required fields are present in the AI feedback response JSON.
     *
     * Required fields:
     * - feedback: Detailed review text in specified language
     * - purchase_intent: Integer 1-10
     * - key_concerns: Array of concerns/hesitations
     *
     * @param feedbackData JsonNode with feedback data from AI
     * @throws AIGatewayException if any required field is missing or null
     */
    private void validateFeedbackResponse(JsonNode feedbackData) {
        String[] requiredFields = {"feedback", "purchase_intent", "key_concerns"};

        for (String field : requiredFields) {
            if (!feedbackData.has(field) || feedbackData.get(field).isNull()) {
                String message = String.format(
                    "Missing or null required field in AI feedback response: '%s'. " +
                    "Expected JSON structure: {\"feedback\": \"...\", \"purchase_intent\": 7, \"key_concerns\": [...]}",
                    field
                );
                log.error("Feedback validation failed: {}", message);
                throw new AIGatewayException(
                    message,
                    ErrorCode.INVALID_AI_RESPONSE.getCode()
                );
            }
        }

        // Validate purchase_intent is within range
        int purchaseIntent = feedbackData.get("purchase_intent").asInt();
        if (purchaseIntent < 1 || purchaseIntent > 10) {
            String message = String.format(
                "purchase_intent must be between 1 and 10, got: %d",
                purchaseIntent
            );
            log.error("Feedback validation failed: {}", message);
            throw new AIGatewayException(
                message,
                ErrorCode.INVALID_AI_RESPONSE.getCode()
            );
        }

        // Validate key_concerns is an array
        if (!feedbackData.get("key_concerns").isArray()) {
            String message = "key_concerns must be an array";
            log.error("Feedback validation failed: {}", message);
            throw new AIGatewayException(
                message,
                ErrorCode.INVALID_AI_RESPONSE.getCode()
            );
        }
    }

    /**
     * Extracts key concerns array from JSON node.
     *
     * @param keyConcernsNode JsonNode containing key_concerns array
     * @return List of concern strings
     */
    private List<String> extractKeyConcerns(JsonNode keyConcernsNode) {
        List<String> concerns = new ArrayList<>();
        if (keyConcernsNode != null && keyConcernsNode.isArray()) {
            for (JsonNode concernNode : keyConcernsNode) {
                concerns.add(concernNode.asText());
            }
        }
        return concerns;
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
