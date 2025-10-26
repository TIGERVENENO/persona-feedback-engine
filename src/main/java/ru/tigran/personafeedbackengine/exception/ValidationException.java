package ru.tigran.personafeedbackengine.exception;

/**
 * Thrown for business logic validation failures.
 * Examples: prompt exceeds max length, too many products/personas in request, referenced entity doesn't exist.
 * HTTP status: 400 Bad Request
 */
public class ValidationException extends ApplicationException {
    public ValidationException(String message, String errorCode) {
        super(message, errorCode);
    }
}
