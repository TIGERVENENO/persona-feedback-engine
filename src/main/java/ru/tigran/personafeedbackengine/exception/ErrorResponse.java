package ru.tigran.personafeedbackengine.exception;

public record ErrorResponse(
        String errorCode,
        String message
) {
}
