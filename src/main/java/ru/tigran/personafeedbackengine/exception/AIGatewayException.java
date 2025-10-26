package ru.tigran.personafeedbackengine.exception;

/**
 * Thrown when AI provider API calls fail (OpenRouter, AgentRouter, etc).
 * Wraps HTTP errors and parsing failures from AI service responses.
 * HTTP status: 500 Internal Server Error
 */
public class AIGatewayException extends ApplicationException {
    public AIGatewayException(String message, String errorCode) {
        super(message, errorCode);
    }

    public AIGatewayException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
