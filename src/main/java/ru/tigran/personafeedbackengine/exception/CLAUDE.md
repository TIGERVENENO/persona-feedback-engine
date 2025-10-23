# exception/

## Purpose
Custom exceptions and global error handling for the application.

## Custom Exceptions

### ResourceNotFoundException
- Thrown when entity (User, Persona, Product, FeedbackSession) is not found
- HTTP status: 404

### UnauthorizedException (or ForbiddenException)
- Thrown when user attempts to access entity not owned by them
- HTTP status: 403

### ValidationException
- Thrown for business logic validation failures:
  - Persona prompt exceeds max length
  - Too many products/personas in request
  - Referenced entity doesn't exist or not owned
- HTTP status: 400

### AIGatewayException
- Thrown when OpenRouter API call fails
- Wraps HTTP errors and parsing failures
- HTTP status: 500 (service error)

## GlobalExceptionHandler
- Spring `@ControllerAdvice` bean
- Maps exceptions to structured error responses:
  ```json
  {
    "errorCode": "PERSONA_NOT_FOUND",
    "message": "Persona with id 123 not found"
  }
  ```
- Handles common Spring exceptions (validation errors, etc.)
- Logs errors with appropriate severity levels

## Error Codes
- `PERSONA_NOT_FOUND`
- `PRODUCT_NOT_FOUND`
- `USER_NOT_FOUND`
- `SESSION_NOT_FOUND`
- `UNAUTHORIZED_ACCESS`
- `VALIDATION_ERROR`
- `INVALID_PROMPT_LENGTH`
- `TOO_MANY_PRODUCTS`
- `TOO_MANY_PERSONAS`
- `AI_SERVICE_ERROR`
- `INTERNAL_SERVER_ERROR`
