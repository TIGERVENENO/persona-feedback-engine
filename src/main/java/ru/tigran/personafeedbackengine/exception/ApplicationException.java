package ru.tigran.personafeedbackengine.exception;

/**
 * Base exception class for all application-specific exceptions.
 * Provides common functionality for error codes and messages.
 */
public abstract class ApplicationException extends RuntimeException {
    private final String errorCode;

    public ApplicationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApplicationException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
