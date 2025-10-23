package ru.tigran.personafeedbackengine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.tigran.personafeedbackengine.exception.AIGatewayException;

@Slf4j
@Service
public class AIGatewayService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final long retryDelayMs;
    private final int maxRetries = 3;

    private static final String OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions";

    public AIGatewayService(
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${app.openrouter.api-key}") String apiKey,
            @Value("${app.openrouter.model}") String model,
            @Value("${app.openrouter.retry-delay-ms}") long retryDelayMs
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.retryDelayMs = retryDelayMs;
    }

    /**
     * Generates detailed persona information from a user prompt.
     * This method is cacheable by prompt to enable persona reusability.
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
     */
    @Cacheable(value = "personaCache", key = "#userPrompt")
    public String generatePersonaDetails(String userPrompt) {
        log.info("Generating persona details for prompt: {}", userPrompt);

        String systemPrompt = """
                You are an AI persona generation expert. Generate a detailed and realistic persona based on the user's prompt.
                Return ONLY valid JSON (no markdown, no extra text) with abbreviated keys to minimize tokens:
                {
                  "nm": "persona name (string)",
                  "dd": "detailed description (2-3 sentences about background, interests, behaviors)",
                  "g": "gender (string)",
                  "ag": "age group (e.g., '25-35', '45-55')",
                  "r": "race/ethnicity (string)",
                  "au": "avatar url placeholder (string, can be empty)"
                }""";

        String userMessage = "Create a persona based on this description: " + userPrompt;

        String response = callOpenRouterAPI(systemPrompt, userMessage);
        validateJSON(response);
        return response;
    }

    /**
     * Generates feedback from a persona about a product.
     * Not cacheable as feedback is user/session-specific and volatile.
     *
     * Expected response: plain text feedback (2-3 sentences)
     */
    public String generateFeedbackForProduct(String personaDescription, String productDescription) {
        log.info("Generating feedback for product");

        String systemPrompt = """
                You are a realistic product reviewer embodying a specific persona.
                Generate authentic, constructive feedback from the perspective of the given persona.
                Return ONLY the feedback text (no JSON, no labels, no extra formatting).""";

        String userMessage = String.format(
                "Persona: %s\n\nProduct: %s\n\nProvide your honest feedback on this product:",
                personaDescription, productDescription
        );

        String response = callOpenRouterAPI(systemPrompt, userMessage);
        return response.trim();
    }

    /**
     * Calls OpenRouter API with retry logic for 429 (rate limit) errors.
     */
    private String callOpenRouterAPI(String systemPrompt, String userMessage) {
        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                log.debug("Calling OpenRouter API, attempt {}", attempt + 1);
                String requestBody = buildRequestBody(systemPrompt, userMessage);

                String response = restClient.post()
                        .uri(OPENROUTER_API_URL)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .body(requestBody)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (request, response1) -> {
                            int statusCode = response1.getStatusCode().value();
                            if (statusCode == 429) {
                                log.warn("Rate limited (429), will retry");
                                throw new RateLimitedException("Rate limited by OpenRouter");
                            }
                            log.error("OpenRouter API error: {} {}", statusCode, response1.getStatusText());
                            throw new AIGatewayException("OpenRouter API error: " + statusCode, "AI_SERVICE_ERROR");
                        })
                        .body(String.class);

                return extractMessageContent(response);

            } catch (RateLimitedException e) {
                attempt++;
                if (attempt >= maxRetries) {
                    throw new AIGatewayException("Max retries exceeded for rate limit", "AI_SERVICE_ERROR", e);
                }
                try {
                    long backoffMs = retryDelayMs * (long) Math.pow(2, attempt - 1);
                    log.info("Retrying after {} ms", backoffMs);
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AIGatewayException("Interrupted during retry", "AI_SERVICE_ERROR", ie);
                }
            } catch (Exception e) {
                log.error("Error calling OpenRouter API", e);
                throw new AIGatewayException("Failed to call OpenRouter API: " + e.getMessage(), "AI_SERVICE_ERROR", e);
            }
        }
        throw new AIGatewayException("Failed after max retries", "AI_SERVICE_ERROR");
    }

    /**
     * Builds the OpenRouter API request body.
     */
    private String buildRequestBody(String systemPrompt, String userMessage) {
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
            throw new AIGatewayException("Failed to build request body", "AI_SERVICE_ERROR", e);
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
                throw new AIGatewayException("Missing content in API response", "AI_SERVICE_ERROR");
            }
            return content.asText();
        } catch (AIGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new AIGatewayException("Failed to parse API response: " + e.getMessage(), "AI_SERVICE_ERROR", e);
        }
    }

    /**
     * Validates that a string is valid JSON.
     */
    private void validateJSON(String json) {
        try {
            objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AIGatewayException("Invalid JSON in response: " + e.getMessage(), "AI_SERVICE_ERROR", e);
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
