package ru.tigran.personafeedbackengine.exception;

/**
 * Enum for application error codes.
 * Centralizes all error code definitions to avoid magic strings.
 * Each code has a default message for logging purposes.
 */
public enum ErrorCode {
    // Resource not found errors
    PERSONA_NOT_FOUND("PERSONA_NOT_FOUND", "Persona not found"),
    PRODUCT_NOT_FOUND("PRODUCT_NOT_FOUND", "Product not found"),
    USER_NOT_FOUND("USER_NOT_FOUND", "User not found"),
    SESSION_NOT_FOUND("SESSION_NOT_FOUND", "Feedback session not found"),
    FEEDBACK_RESULT_NOT_FOUND("FEEDBACK_RESULT_NOT_FOUND", "Feedback result not found"),

    // Authorization errors
    UNAUTHORIZED_ACCESS("UNAUTHORIZED_ACCESS", "Unauthorized access to resource"),

    // Validation errors
    VALIDATION_ERROR("VALIDATION_ERROR", "Validation failed"),
    INVALID_PROMPT_LENGTH("INVALID_PROMPT_LENGTH", "Prompt exceeds maximum length"),
    TOO_MANY_PRODUCTS("TOO_MANY_PRODUCTS", "Too many products in request"),
    TOO_MANY_PERSONAS("TOO_MANY_PERSONAS", "Too many personas in request"),
    PERSONAS_NOT_READY("PERSONAS_NOT_READY", "Some personas are not ready for use"),
    DUPLICATE_REQUEST("DUPLICATE_REQUEST", "Duplicate request detected"),
    PERSONA_GENERATION_FAILED("PERSONA_GENERATION_FAILED", "Persona generation failed"),
    PERSONA_GENERATION_TIMEOUT("PERSONA_GENERATION_TIMEOUT", "Timeout waiting for persona generation"),

    // Authentication errors
    EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", "Email already registered"),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "Invalid email or password"),
    USER_INACTIVE("USER_INACTIVE", "User account is inactive or deleted"),

    // AI service errors
    AI_SERVICE_ERROR("AI_SERVICE_ERROR", "AI service error"),
    INVALID_AI_RESPONSE("INVALID_AI_RESPONSE", "Invalid response from AI service"),
    INVALID_JSON_RESPONSE("INVALID_JSON_RESPONSE", "Failed to parse JSON response from AI"),

    // Internal server errors
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "An unexpected error occurred");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
