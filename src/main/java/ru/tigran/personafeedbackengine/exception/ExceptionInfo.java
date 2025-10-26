package ru.tigran.personafeedbackengine.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Internal class used by GlobalExceptionHandler to map exception types to HTTP status codes
 * and logging levels.
 */
record ExceptionInfo(HttpStatus status, boolean shouldLogError) {
    /**
     * Returns ExceptionInfo for given ApplicationException type.
     * Used to determine appropriate HTTP status and logging level.
     *
     * @param exception ApplicationException instance
     * @return ExceptionInfo with status and logging configuration
     */
    static ExceptionInfo forException(ApplicationException exception) {
        if (exception instanceof ResourceNotFoundException) {
            return new ExceptionInfo(HttpStatus.NOT_FOUND, false);
        } else if (exception instanceof UnauthorizedException) {
            return new ExceptionInfo(HttpStatus.FORBIDDEN, false);
        } else if (exception instanceof ValidationException) {
            return new ExceptionInfo(HttpStatus.BAD_REQUEST, false);
        } else if (exception instanceof AIGatewayException) {
            return new ExceptionInfo(HttpStatus.INTERNAL_SERVER_ERROR, true);
        }
        // Default for unknown ApplicationException subtypes
        return new ExceptionInfo(HttpStatus.INTERNAL_SERVER_ERROR, true);
    }
}
