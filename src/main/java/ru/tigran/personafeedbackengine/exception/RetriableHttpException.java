package ru.tigran.personafeedbackengine.exception;

/**
 * Thrown for transient HTTP errors that should be retried.
 * Examples: 429 (Too Many Requests), 502 (Bad Gateway), 503 (Service Unavailable), 504 (Gateway Timeout)
 *
 * This exception is retriable by design.
 */
public class RetriableHttpException extends RuntimeException {
    private final int statusCode;

    public RetriableHttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public RetriableHttpException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
