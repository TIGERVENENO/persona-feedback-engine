package ru.tigran.personafeedbackengine.exception;

/**
 * Base exception class for all application-specific exceptions.
 * Provides common functionality for error codes, messages, and retriability.
 *
 * Retriable exceptions indicate transient errors that can be retried (e.g., 429, 502, 503, 504).
 * Non-retriable exceptions indicate permanent errors that should not be retried.
 */
public abstract class ApplicationException extends RuntimeException {
    private final String errorCode;
    private final boolean retriable;

    public ApplicationException(String message, String errorCode) {
        this(message, errorCode, false);
    }

    public ApplicationException(String message, String errorCode, boolean retriable) {
        super(message);
        this.errorCode = errorCode;
        this.retriable = retriable;
    }

    public ApplicationException(String message, String errorCode, Throwable cause) {
        this(message, errorCode, false, cause);
    }

    public ApplicationException(String message, String errorCode, boolean retriable, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retriable = retriable;
    }

    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns true if this exception represents a transient error that can be retried.
     * Examples: 429 (Too Many Requests), 502 (Bad Gateway), 503 (Service Unavailable), 504 (Gateway Timeout)
     *
     * @return true if retriable, false if permanent error
     */
    public boolean isRetriable() {
        return retriable;
    }
}
