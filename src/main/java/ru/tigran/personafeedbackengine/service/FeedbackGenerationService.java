package ru.tigran.personafeedbackengine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tigran.personafeedbackengine.dto.AggregatedInsights;
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
import java.util.stream.Collectors;

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

        // Validate persona has required fields (#3.1 - Null Pointer Exception checks)
        if (persona.getDetailedDescription() == null || persona.getDetailedDescription().isBlank()) {
            String errorMsg = String.format(
                "Persona %d is missing required field: detailedDescription. " +
                "Persona must be fully generated (state=ACTIVE) before feedback generation.",
                persona.getId()
            );
            log.error(errorMsg);
            throw new AIGatewayException(
                errorMsg,
                ErrorCode.INVALID_AI_RESPONSE.getCode()
            );
        }

        if (persona.getProductAttitudes() == null || persona.getProductAttitudes().isBlank()) {
            String errorMsg = String.format(
                "Persona %d is missing required field: productAttitudes. " +
                "Persona must be fully generated (state=ACTIVE) before feedback generation.",
                persona.getId()
            );
            log.error(errorMsg);
            throw new AIGatewayException(
                errorMsg,
                ErrorCode.INVALID_AI_RESPONSE.getCode()
            );
        }

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
     * Performs comprehensive validation including type checks, range checks, and size constraints.
     *
     * Required fields with constraints:
     * - feedback: Detailed review text (non-empty string, max 2000 chars)
     * - purchase_intent: Integer 1-10
     * - key_concerns: Array of 2-4 string concerns
     *
     * @param feedbackData JsonNode with feedback data from AI
     * @throws AIGatewayException if any field is invalid, missing, or doesn't meet constraints
     */
    private void validateFeedbackResponse(JsonNode feedbackData) {
        // (#3.2 - Incomplete JSON Validation) - Comprehensive validation

        String[] requiredFields = {"feedback", "purchase_intent", "key_concerns"};

        // 1. Check all required fields exist and are not null
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

        // 2. Validate feedback field (string, non-empty, max 2000 chars)
        JsonNode feedbackNode = feedbackData.get("feedback");
        if (!feedbackNode.isTextual()) {
            String message = "feedback field must be a string";
            log.error("Feedback validation failed: {}", message);
            throw new AIGatewayException(message, ErrorCode.INVALID_AI_RESPONSE.getCode());
        }

        String feedbackText = feedbackNode.asText();
        if (feedbackText.isBlank()) {
            String message = "feedback field must not be empty";
            log.error("Feedback validation failed: {}", message);
            throw new AIGatewayException(message, ErrorCode.INVALID_AI_RESPONSE.getCode());
        }

        if (feedbackText.length() > 2000) {
            String message = String.format(
                "feedback field exceeds maximum length of 2000 characters (got %d chars)",
                feedbackText.length()
            );
            log.error("Feedback validation failed: {}", message);
            throw new AIGatewayException(message, ErrorCode.INVALID_AI_RESPONSE.getCode());
        }

        // 3. Validate purchase_intent field (integer, range 1-10)
        JsonNode intentNode = feedbackData.get("purchase_intent");
        if (!intentNode.isIntegralNumber()) {
            String message = "purchase_intent must be an integer";
            log.error("Feedback validation failed: {}", message);
            throw new AIGatewayException(message, ErrorCode.INVALID_AI_RESPONSE.getCode());
        }

        int purchaseIntent = intentNode.asInt();
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

        // 4. Validate key_concerns field (array of 2-4 string items)
        JsonNode concernsNode = feedbackData.get("key_concerns");
        if (!concernsNode.isArray()) {
            String message = "key_concerns must be an array";
            log.error("Feedback validation failed: {}", message);
            throw new AIGatewayException(
                message,
                ErrorCode.INVALID_AI_RESPONSE.getCode()
            );
        }

        int concernCount = concernsNode.size();
        if (concernCount < 2 || concernCount > 4) {
            String message = String.format(
                "key_concerns must contain between 2 and 4 items, got: %d",
                concernCount
            );
            log.error("Feedback validation failed: {}", message);
            throw new AIGatewayException(message, ErrorCode.INVALID_AI_RESPONSE.getCode());
        }

        // 5. Validate each concern is a non-empty string
        for (int i = 0; i < concernCount; i++) {
            JsonNode concern = concernsNode.get(i);
            if (!concern.isTextual()) {
                String message = String.format(
                    "key_concerns[%d] must be a string, got: %s",
                    i, concern.getNodeType()
                );
                log.error("Feedback validation failed: {}", message);
                throw new AIGatewayException(message, ErrorCode.INVALID_AI_RESPONSE.getCode());
            }

            String concernText = concern.asText();
            if (concernText.isBlank()) {
                String message = String.format("key_concerns[%d] must not be empty", i);
                log.error("Feedback validation failed: {}", message);
                throw new AIGatewayException(message, ErrorCode.INVALID_AI_RESPONSE.getCode());
            }

            if (concernText.length() > 500) {
                String message = String.format(
                    "key_concerns[%d] exceeds maximum length of 500 characters (got %d chars)",
                    i, concernText.length()
                );
                log.error("Feedback validation failed: {}", message);
                throw new AIGatewayException(message, ErrorCode.INVALID_AI_RESPONSE.getCode());
            }
        }

        log.debug("Feedback validation passed: feedback={} chars, intent={}, concerns={} items",
                feedbackText.length(), purchaseIntent, concernCount);
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
     * When session is complete, aggregates insights from all results.
     *
     * @param sessionId ID of the feedback session
     * @throws AIGatewayException if lock cannot be acquired (should be retried)
     */
    private void checkAndUpdateSessionCompletion(Long sessionId) {
        String lockKey = "feedback-session-lock:" + sessionId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // Try to acquire lock with 10 second timeout
            boolean locked = lock.tryLock(10, TimeUnit.SECONDS);
            if (!locked) {
                // Lock acquisition failed - throw exception to trigger retry through queue
                String errorMsg = String.format(
                    "Failed to acquire lock for feedback session %d after 10 seconds. " +
                    "This likely indicates high contention or Redis connectivity issues. " +
                    "The task will be retried.",
                    sessionId
                );
                log.warn(errorMsg);
                throw new AIGatewayException(
                    errorMsg,
                    ErrorCode.AI_SERVICE_ERROR.getCode(),
                    true  // Mark as retriable so queue consumer retries this
                );
            }

            try {
                FeedbackSession session = feedbackSessionRepository.findByIdForUpdate(sessionId)
                        .orElse(null);

                if (session == null) {
                    log.warn("Feedback session {} not found during completion check", sessionId);
                    return;
                }

                // Get current session status
                SessionStatusInfo statusInfo = feedbackResultRepository.getSessionStatus(sessionId);

                // If all results are done (completed or failed), aggregate insights and mark session as completed
                if (statusInfo.completed() + statusInfo.failed() >= statusInfo.total()) {
                    log.info("Session {} completion criteria met: {}/{} results done",
                            sessionId, statusInfo.completed() + statusInfo.failed(), statusInfo.total());

                    // Агрегируем insights из всех результатов
                    String aggregatedInsightsJson = aggregateSessionInsights(sessionId);

                    // Сохраняем aggregated insights в session
                    session.setAggregatedInsights(aggregatedInsightsJson);
                    feedbackSessionRepository.save(session);

                    // Обновляем статус сессии
                    int updated = feedbackSessionRepository.updateStatusIfNotAlready(
                            sessionId,
                            FeedbackSession.FeedbackSessionStatus.COMPLETED
                    );
                    if (updated > 0) {
                        log.info("Feedback session {} marked as COMPLETED with aggregated insights", sessionId);
                    } else {
                        log.debug("Session {} was already marked as COMPLETED by another instance", sessionId);
                    }
                } else {
                    log.debug("Session {} not yet complete: {}/{} results done",
                            sessionId, statusInfo.completed() + statusInfo.failed(), statusInfo.total());
                }
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMsg = String.format(
                "Interrupted while acquiring lock for feedback session %d. " +
                "The task will be retried.",
                sessionId
            );
            log.warn(errorMsg, e);
            throw new AIGatewayException(
                errorMsg,
                ErrorCode.AI_SERVICE_ERROR.getCode(),
                true  // Mark as retriable
            );
        } catch (AIGatewayException e) {
            // Re-throw AIGatewayException as-is (includes lock acquisition failures)
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format(
                "Unexpected error updating feedback session %d completion status. " +
                "The task will be retried.",
                sessionId
            );
            log.error(errorMsg, e);
            throw new AIGatewayException(
                errorMsg,
                ErrorCode.AI_SERVICE_ERROR.getCode(),
                true  // Mark as retriable so queue consumer retries this
            );
        }
    }

    /**
     * Агрегирует insights из всех completed feedback results сессии.
     *
     * Вычисляет:
     * - averageScore: Средний purchase intent (1-10)
     * - purchaseIntentPercent: Процент персон с purchaseIntent >= 7
     * - keyThemes: Агрегированные темы через AI (из всех keyConcerns)
     *
     * @param sessionId ID сессии
     * @return JSON строка с AggregatedInsights
     */
    private String aggregateSessionInsights(Long sessionId) {
        log.info("Aggregating insights for feedback session {}", sessionId);

        // Загружаем все COMPLETED результаты
        List<FeedbackResult> results = feedbackResultRepository.findByFeedbackSessionId(sessionId)
                .stream()
                .filter(r -> r.getStatus() == FeedbackResult.FeedbackResultStatus.COMPLETED)
                .collect(Collectors.toList());

        if (results.isEmpty()) {
            log.warn("No completed results found for session {}, returning empty insights", sessionId);
            try {
                AggregatedInsights emptyInsights = new AggregatedInsights(0.0, 0, List.of());
                return objectMapper.writeValueAsString(emptyInsights);
            } catch (Exception e) {
                return "{\"averageScore\":0,\"purchaseIntentPercent\":0,\"keyThemes\":[]}";
            }
        }

        // 1. Вычисляем averageScore (средний purchaseIntent)
        double averageScore = results.stream()
                .mapToInt(FeedbackResult::getPurchaseIntent)
                .average()
                .orElse(0.0);

        // 2. Вычисляем purchaseIntentPercent (% с intent >= 7)
        long highIntentCount = results.stream()
                .filter(r -> r.getPurchaseIntent() >= 7)
                .count();
        int purchaseIntentPercent = (int) Math.round((double) highIntentCount / results.size() * 100);

        // 3. Собираем все keyConcerns из всех результатов
        List<String> allConcerns = results.stream()
                .flatMap(r -> r.getKeyConcerns().stream())
                .collect(Collectors.toList());

        // 4. Агрегируем темы через AI
        String keyThemesJson = aiGatewayService.aggregateKeyThemes(allConcerns);

        try {
            // Парсим keyThemes JSON в список KeyTheme объектов
            JsonNode themesNode = objectMapper.readTree(keyThemesJson);
            List<AggregatedInsights.KeyTheme> keyThemes = new ArrayList<>();

            for (JsonNode themeNode : themesNode) {
                String theme = themeNode.get("theme").asText();
                Integer mentions = themeNode.get("mentions").asInt();
                keyThemes.add(new AggregatedInsights.KeyTheme(theme, mentions));
            }

            // Создаём AggregatedInsights DTO
            AggregatedInsights insights = new AggregatedInsights(
                    averageScore,
                    purchaseIntentPercent,
                    keyThemes
            );

            // Сериализуем в JSON
            String insightsJson = objectMapper.writeValueAsString(insights);
            log.info("Aggregated insights for session {}: avgScore={}, intentPercent={}%, themes={}",
                    sessionId, averageScore, purchaseIntentPercent, keyThemes.size());

            return insightsJson;

        } catch (Exception e) {
            log.error("Failed to aggregate session insights for session {}", sessionId, e);
            throw new RuntimeException("Failed to aggregate session insights", e);
        }
    }
}
