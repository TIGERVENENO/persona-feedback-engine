package ru.tigran.personafeedbackengine.exception;

/**
 * Thrown when AI provider API calls fail (OpenRouter, AgentRouter, etc).
 * Wraps HTTP errors and parsing failures from AI service responses.
 * HTTP status: 500 Internal Server Error
 *
 * Can be retriable (for transient errors like 429, 502, 503, 504)
 * or non-retriable (for permanent errors like 401, 403, 400).
 */
public class AIGatewayException extends ApplicationException {
    public AIGatewayException(String message, String errorCode) {
        super(message, errorCode);
    }

    public AIGatewayException(String message, String errorCode, boolean retriable) {
        super(message, errorCode, retriable);
    }

    public AIGatewayException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, false, cause);
    }

    public AIGatewayException(String message, String errorCode, boolean retriable, Throwable cause) {
        super(message, errorCode, retriable, cause);
    }
}
