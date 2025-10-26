package ru.tigran.personafeedbackengine.exception;

/**
 * Thrown when a user attempts to access a resource they are not authorized to access.
 * HTTP status: 403 Forbidden
 */
public class UnauthorizedException extends ApplicationException {
    public UnauthorizedException(String message, String errorCode) {
        super(message, errorCode);
    }
}
