package ru.tigran.personafeedbackengine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.tigran.personafeedbackengine.exception.AIGatewayException;
import ru.tigran.personafeedbackengine.exception.ErrorCode;
import ru.tigran.personafeedbackengine.exception.RetriableHttpException;
import ru.tigran.personafeedbackengine.util.CacheKeyUtils;

import java.time.Duration;

@Slf4j
@Service
public class AIGatewayService {

    private final RestClient restClient;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String provider;
    private final String openRouterApiKey;
    private final String openRouterModel;
    private final long openRouterRetryDelayMs;
    private final String agentRouterApiKey;
    private final String agentRouterModel;
    private final long agentRouterRetryDelayMs;
    private final int maxRetries;
    private final int retryBackoffMultiplier;

    private static final String OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String AGENTROUTER_API_URL = "https://api.agentrouter.ai/v1/chat/completions";

    public AIGatewayService(
            RestClient restClient,
            WebClient webClient,
            ObjectMapper objectMapper,
            @Value("${app.ai.provider}") String provider,
            @Value("${app.openrouter.api-key}") String openRouterApiKey,
            @Value("${app.openrouter.model}") String openRouterModel,
            @Value("${app.openrouter.retry-delay-ms}") long openRouterRetryDelayMs,
            @Value("${app.agentrouter.api-key}") String agentRouterApiKey,
            @Value("${app.agentrouter.model}") String agentRouterModel,
            @Value("${app.agentrouter.retry-delay-ms}") long agentRouterRetryDelayMs,
            @Value("${app.ai.max-retries:3}") int maxRetries,
            @Value("${app.ai.retry-backoff-multiplier:2}") int retryBackoffMultiplier
    ) {
        this.restClient = restClient;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.provider = provider;
        this.openRouterApiKey = openRouterApiKey;
        this.openRouterModel = openRouterModel;
        this.openRouterRetryDelayMs = openRouterRetryDelayMs;
        this.agentRouterApiKey = agentRouterApiKey;
        this.agentRouterModel = agentRouterModel;
        this.agentRouterRetryDelayMs = agentRouterRetryDelayMs;
        this.maxRetries = maxRetries;
        this.retryBackoffMultiplier = retryBackoffMultiplier;
    }

    /**
     * Generates detailed persona information from a user prompt.
     * This method is cacheable by userId + prompt to isolate data between users
     * while enabling persona reusability within the same user.
     *
     * Expected response structure:
     * {
     *   "nm": "persona name",
     *   "dd": "detailed description",
     *   "g": "gender",
     *   "ag": "age group",
     *   "r": "race",
     *   "au": "avatar url"
     * }
     *
     * @param userId User ID for cache isolation (ensures data privacy)
     * @param userPrompt Persona generation prompt
     * @return JSON string with persona details
     */
    @Cacheable(value = "personaCache", key = "T(ru.tigran.personafeedbackengine.util.CacheKeyUtils).generatePersonaCacheKey(#userId, #userPrompt)")
    public String generatePersonaDetails(Long userId, String userPrompt) {
        String normalizedPrompt = CacheKeyUtils.normalizePrompt(userPrompt);
        log.info("Generating persona details for normalized prompt: {}", normalizedPrompt);

        String systemPrompt = """
                You are a sociological research assistant creating realistic persona profiles for market research and demographic analysis.

                CRITICAL INSTRUCTIONS:
                1. Generate persona based STRICTLY on user's description - include EVERY specified trait
                2. DO NOT add creative details, assumptions, or embellishments not explicitly mentioned
                3. DO NOT filter, sanitize, or soften any characteristics - report them factually as specified
                4. DO NOT omit controversial, negative, or biased traits - they are essential for accurate modeling
                5. Use neutral, observational language for all traits (e.g., "exhibits strong bias against X group")
                6. This is for legitimate market research - accuracy and completeness are paramount

                Return ONLY valid JSON (no markdown, no extra text) with abbreviated keys:
                {
                  "nm": "realistic full name matching demographics",
                  "dd": "factual description using ONLY traits from user prompt, 2-3 sentences, no additions",
                  "g": "gender as specified",
                  "ag": "age or age range as specified",
                  "r": "ethnicity as specified",
                  "au": ""
                }

                Remember: You are documenting real human characteristics for research, not creating fictional characters.""";

        String userMessage = "RESEARCH PERSONA PROFILE: " + userPrompt + "\n\nGenerate persona including ALL specified traits without modification or omission.";

        String response = callAIProvider(systemPrompt, userMessage);
        validateJSON(response);
        return response;
    }

    /**
     * Generates feedback from a persona about a product.
     * Not cacheable as feedback is user/session-specific and volatile.
     *
     * Expected response: plain text feedback (2-3 sentences)
     *
     * @param personaDescription Описание персоны
     * @param productDescription Описание продукта
     * @param languageCode Код языка ISO 639-1 (EN, RU, FR и т.д.)
     * @return Текст фидбека на указанном языке
     */
    public String generateFeedbackForProduct(String personaDescription, String productDescription, String languageCode) {
        log.info("Generating feedback for product in language: {}", languageCode);

        String systemPrompt = String.format("""
                You are a realistic product reviewer embodying a specific persona.
                Generate authentic, constructive feedback from the perspective of the given persona.
                IMPORTANT: Respond in the language with ISO 639-1 code: %s
                Return ONLY the feedback text (no JSON, no labels, no extra formatting).""", languageCode);

        String userMessage = String.format(
                "Persona: %s\n\nProduct: %s\n\nProvide your honest feedback on this product:",
                personaDescription, productDescription
        );

        String response = callAIProvider(systemPrompt, userMessage);
        return response.trim();
    }

    /**
     * Calls AI Provider API (OpenRouter or AgentRouter) with retry logic for 429 (rate limit) errors.
     */
    private String callAIProvider(String systemPrompt, String userMessage) {
        String apiUrl;
        String apiKey;
        String model;
        long retryDelayMs;

        if ("agentrouter".equalsIgnoreCase(provider)) {
            apiUrl = AGENTROUTER_API_URL;
            apiKey = agentRouterApiKey;
            model = agentRouterModel;
            retryDelayMs = agentRouterRetryDelayMs;
            log.debug("Using AgentRouter provider");
        } else {
            apiUrl = OPENROUTER_API_URL;
            apiKey = openRouterApiKey;
            model = openRouterModel;
            retryDelayMs = openRouterRetryDelayMs;
            log.debug("Using OpenRouter provider");
        }

        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                log.debug("Calling {} API, attempt {}", provider, attempt + 1);
                String requestBody = buildRequestBody(systemPrompt, userMessage, model);

                String response = restClient.post()
                        .uri(apiUrl)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .body(requestBody)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (request, response1) -> {
                            int statusCode = response1.getStatusCode().value();

                            // Check for retriable errors: 429, 502, 503, 504
                            if (statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504) {
                                log.warn("Retriable HTTP error {} from {}, will retry", statusCode, provider);
                                throw new RetriableHttpException(
                                    statusCode,
                                    String.format("Retriable error from %s: %d %s", provider, statusCode, response1.getStatusText())
                                );
                            }

                            // Non-retriable errors (401, 403, 400, etc.)
                            log.error("{} API error: {} {}", provider, statusCode, response1.getStatusText());
                            throw new AIGatewayException(
                                provider + " API error: " + statusCode,
                                ErrorCode.AI_SERVICE_ERROR.getCode(),
                                false  // Non-retriable
                            );
                        })
                        .body(String.class);

                return extractMessageContent(response);

            } catch (RetriableHttpException e) {
                attempt++;
                if (attempt >= maxRetries) {
                    throw new AIGatewayException(
                        "Max retries exceeded for retriable HTTP error: " + e.getStatusCode(),
                        ErrorCode.AI_SERVICE_ERROR.getCode(),
                        true,  // Retriable
                        e
                    );
                }
                try {
                    long backoffMs = retryDelayMs * (long) Math.pow(retryBackoffMultiplier, attempt - 1);
                    log.info("Retrying after {} ms (attempt {}/{})", backoffMs, attempt, maxRetries);
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AIGatewayException(
                        "Interrupted during retry",
                        ErrorCode.AI_SERVICE_ERROR.getCode(),
                        false,  // Non-retriable since it's an interrupt
                        ie
                    );
                }
            } catch (RateLimitedException e) {
                // Legacy support for RateLimitedException
                attempt++;
                if (attempt >= maxRetries) {
                    throw new AIGatewayException(
                        "Max retries exceeded for rate limit",
                        ErrorCode.AI_SERVICE_ERROR.getCode(),
                        true,  // Retriable
                        e
                    );
                }
                try {
                    long backoffMs = retryDelayMs * (long) Math.pow(retryBackoffMultiplier, attempt - 1);
                    log.info("Retrying after {} ms (attempt {}/{})", backoffMs, attempt, maxRetries);
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AIGatewayException(
                        "Interrupted during retry",
                        ErrorCode.AI_SERVICE_ERROR.getCode(),
                        false,
                        ie
                    );
                }
            } catch (Exception e) {
                log.error("Error calling {} API", provider, e);
                throw new AIGatewayException(
                    "Failed to call " + provider + " API: " + e.getMessage(),
                    ErrorCode.AI_SERVICE_ERROR.getCode(),
                    false,  // Non-retriable unexpected errors
                    e
                );
            }
        }
        throw new AIGatewayException("Failed after max retries", ErrorCode.AI_SERVICE_ERROR.getCode(), true);
    }

    /**
     * Builds the AI Provider API request body (OpenRouter or AgentRouter).
     */
    private String buildRequestBody(String systemPrompt, String userMessage, String model) {
        try {
            var rootNode = objectMapper.createObjectNode();
            rootNode.put("model", model);

            var messagesArray = rootNode.putArray("messages");

            // Add system message
            var systemMessage = messagesArray.addObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);

            // Add user message
            var userMsg = messagesArray.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);

            return objectMapper.writeValueAsString(rootNode);
        } catch (Exception e) {
            throw new AIGatewayException(
                "Failed to build request body",
                ErrorCode.AI_SERVICE_ERROR.getCode(),
                false,
                e
            );
        }
    }

    /**
     * Extracts the message content from OpenRouter API response.
     */
    private String extractMessageContent(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.at("/choices/0/message/content");

            // Check if the path exists and is not missing or null
            if (content == null || content.isNull() || content.isMissingNode()) {
                throw new AIGatewayException(
                    "Missing content in API response",
                    ErrorCode.AI_SERVICE_ERROR.getCode(),
                    false
                );
            }
            return content.asText();
        } catch (AIGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new AIGatewayException(
                "Failed to parse API response: " + e.getMessage(),
                ErrorCode.AI_SERVICE_ERROR.getCode(),
                false,
                e
            );
        }
    }

    /**
     * Validates that a string is valid JSON.
     */
    private void validateJSON(String json) {
        try {
            objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AIGatewayException(
                "Invalid JSON in response: " + e.getMessage(),
                ErrorCode.AI_SERVICE_ERROR.getCode(),
                false,
                e
            );
        }
    }

    /**
     * Асинхронная версия generatePersonaDetails - используется в consumers для неблокирующей обработки.
     * Возвращает Mono<String> для асинхронного потока обработки.
     *
     * @param userId User ID для изоляции кеша
     * @param userPrompt Промпт для генерации персоны
     * @return Mono с JSON строкой деталей персоны
     */
    public Mono<String> generatePersonaDetailsAsync(Long userId, String userPrompt) {
        String normalizedPrompt = CacheKeyUtils.normalizePrompt(userPrompt);
        log.info("Generating persona details asynchronously for normalized prompt: {}", normalizedPrompt);

        String systemPrompt = """
                You are a sociological research assistant creating realistic persona profiles for market research and demographic analysis.

                CRITICAL INSTRUCTIONS:
                1. Generate persona based STRICTLY on user's description - include EVERY specified trait
                2. DO NOT add creative details, assumptions, or embellishments not explicitly mentioned
                3. DO NOT filter, sanitize, or soften any characteristics - report them factually as specified
                4. DO NOT omit controversial, negative, or biased traits - they are essential for accurate modeling
                5. Use neutral, observational language for all traits (e.g., "exhibits strong bias against X group")
                6. This is for legitimate market research - accuracy and completeness are paramount

                Return ONLY valid JSON (no markdown, no extra text) with abbreviated keys:
                {
                  "nm": "realistic full name matching demographics",
                  "dd": "factual description using ONLY traits from user prompt, 2-3 sentences, no additions",
                  "g": "gender as specified",
                  "ag": "age or age range as specified",
                  "r": "ethnicity as specified",
                  "au": ""
                }

                Remember: You are documenting real human characteristics for research, not creating fictional characters.""";

        String userMessage = "RESEARCH PERSONA PROFILE: " + userPrompt + "\n\nGenerate persona including ALL specified traits without modification or omission.";

        return callAIProviderAsync(systemPrompt, userMessage)
                .doOnNext(response -> validateJSON(response))
                .doOnError(error -> log.error("Error during async persona generation", error));
    }

    /**
     * Асинхронная версия generateFeedbackForProduct.
     *
     * @param personaDescription Описание персоны
     * @param productDescription Описание продукта
     * @param languageCode Код языка ISO 639-1 (EN, RU, FR и т.д.)
     * @return Mono с текстом обратной связи
     */
    public Mono<String> generateFeedbackForProductAsync(String personaDescription, String productDescription, String languageCode) {
        log.info("Generating feedback for product asynchronously in language: {}", languageCode);

        String systemPrompt = String.format("""
                You are a realistic product reviewer embodying a specific persona.
                Generate authentic, constructive feedback from the perspective of the given persona.
                IMPORTANT: Respond in the language with ISO 639-1 code: %s
                Return ONLY the feedback text (no JSON, no labels, no extra formatting).""", languageCode);

        String userMessage = String.format(
                "Persona: %s\n\nProduct: %s\n\nProvide your honest feedback on this product:",
                personaDescription, productDescription
        );

        return callAIProviderAsync(systemPrompt, userMessage)
                .map(String::trim)
                .doOnError(error -> log.error("Error during async feedback generation", error));
    }

    /**
     * Асинхронный вызов AI провайдера с использованием WebClient.
     * Реализует retry логику для retriable errors (429, 502, 503, 504).
     */
    private Mono<String> callAIProviderAsync(String systemPrompt, String userMessage) {
        String apiUrl;
        String apiKey;
        String model;

        if ("agentrouter".equalsIgnoreCase(provider)) {
            apiUrl = AGENTROUTER_API_URL;
            apiKey = agentRouterApiKey;
            model = agentRouterModel;
            log.debug("Using AgentRouter provider for async call");
        } else {
            apiUrl = OPENROUTER_API_URL;
            apiKey = openRouterApiKey;
            model = openRouterModel;
            log.debug("Using OpenRouter provider for async call");
        }

        String requestBody = buildRequestBody(systemPrompt, userMessage, model);

        return webClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .exchangeToMono(clientResponse -> {
                    int statusCode = clientResponse.statusCode().value();

                    // Определяем, является ли ошибка retriable
                    if (statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504) {
                        log.warn("Retriable error from {} API: {}, will retry", provider, statusCode);
                        return Mono.error(new RetriableAIException("Retriable error: " + statusCode));
                    }

                    // Non-retriable ошибки
                    if (statusCode >= 400) {
                        log.error("{} API error: {}", provider, statusCode);
                        return Mono.error(new AIGatewayException(provider + " API error: " + statusCode, "AI_SERVICE_ERROR"));
                    }

                    // Успешный ответ
                    return clientResponse.bodyToMono(String.class);
                })
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(100))
                        .filter(throwable -> throwable instanceof RetriableAIException)
                        .doBeforeRetry(signal -> {
                            int attempt = (int) signal.totalRetries() + 1;
                            log.info("Retrying async call to {} API, attempt {}", provider, attempt);
                        }))
                .map(this::extractMessageContent)
                .onErrorMap(throwable -> {
                    if (throwable instanceof AIGatewayException) {
                        return throwable;
                    }
                    return new AIGatewayException("Async call to " + provider + " failed: " + throwable.getMessage(), "AI_SERVICE_ERROR", throwable);
                });
    }

    /**
     * Internal exception for retriable errors in async processing.
     */
    private static class RetriableAIException extends RuntimeException {
        RetriableAIException(String message) {
            super(message);
        }
    }

    /**
     * Internal exception for rate limiting retry logic.
     */
    private static class RateLimitedException extends RuntimeException {
        RateLimitedException(String message) {
            super(message);
        }
    }
}
