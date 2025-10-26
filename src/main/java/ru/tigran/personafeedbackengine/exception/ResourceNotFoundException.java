package ru.tigran.personafeedbackengine.exception;

/**
 * Thrown when a requested resource (entity) is not found in the database.
 * HTTP status: 404 Not Found
 */
public class ResourceNotFoundException extends ApplicationException {
    public ResourceNotFoundException(String message, String errorCode) {
        super(message, errorCode);
    }
}
