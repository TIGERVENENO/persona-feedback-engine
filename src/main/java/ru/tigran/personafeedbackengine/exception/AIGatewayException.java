package ru.tigran.personafeedbackengine.exception;

public class AIGatewayException extends RuntimeException {
    private final String errorCode;

    public AIGatewayException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public AIGatewayException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
