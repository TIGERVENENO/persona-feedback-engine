# exception/

## Purpose
Custom exceptions and global error handling for the application.
Follows the Single Responsibility Principle with a shared base exception class.

## Base Exception Class

### ApplicationException
- Abstract base class for all application-specific exceptions
- Provides: error code storage, getter method, constructors (with/without cause)
- All custom exceptions inherit from this class to eliminate duplication
- Extracted common errorCode field and getErrorCode() method used by all exceptions

## Custom Exceptions

All custom exceptions extend ApplicationException:

### ResourceNotFoundException
- Thrown when entity (User, Persona, Product, FeedbackSession) is not found
- HTTP status: 404 Not Found

### UnauthorizedException
- Thrown when user attempts to access entity not owned by them
- HTTP status: 403 Forbidden

### ValidationException
- Thrown for business logic validation failures:
  - Persona prompt exceeds max length
  - Too many products/personas in request
  - Referenced entity doesn't exist or not owned
- HTTP status: 400 Bad Request

### AIGatewayException
- Thrown when AI provider API call fails (OpenRouter, AgentRouter, or any other provider)
- Wraps HTTP errors and parsing failures
- HTTP status: 500 Internal Server Error
- Includes provider-specific error messages
- Supports chaining with root cause (constructor with Throwable parameter)

## Error Mapping

### ExceptionInfo
- Internal class mapping ApplicationException types to HTTP status codes and logging levels
- Static method `forException(ApplicationException)` determines:
  - Appropriate HTTP status (404, 403, 400, 500)
  - Logging level (warn vs error)
- Eliminates switch statements or instanceof chains

## GlobalExceptionHandler
- Spring `@ControllerAdvice` bean
- **Single handler for all ApplicationExceptions** (eliminates 4 duplicate @ExceptionHandler methods)
- Uses ExceptionInfo to determine response status and logging level
- Maps exceptions to structured error responses:
  ```json
  {
    "errorCode": "PERSONA_NOT_FOUND",
    "message": "Persona with id 123 not found"
  }
  ```
- Also handles Spring exceptions (MethodArgumentNotValidException) and generic Exception fallback
- Logs errors with appropriate severity levels based on exception type

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

## Design Improvements (SRP)
**Before**: 4 duplicate @ExceptionHandler methods, each with identical error handling logic
**After**: 1 unified handler leveraging ApplicationException base class and ExceptionInfo mapper
