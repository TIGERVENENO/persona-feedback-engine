# controller/

## Purpose
REST API endpoints for client interaction.

## Key Classes

### PersonaController
- Base path: `/api/v1/personas`
- **POST /api/v1/personas**: Trigger persona generation
  - Request: `PersonaGenerationRequest`
  - Response: `JobResponse` with persona ID and initial status
  - Validates prompt length, ownership, and permissions

### FeedbackController
- Base path: `/api/v1/feedback-sessions`
- **POST /api/v1/feedback-sessions**: Trigger feedback session
  - Request: `FeedbackSessionRequest`
  - Response: `JobResponse` with session ID and initial status
  - Validates product/persona ownership, counts, and existence
- **GET /api/v1/feedback-sessions/{jobId}**: Poll for session status/results
  - Response: `FeedbackSessionResponse` with nested FeedbackResults
  - Returns current status and available feedback results

## Request Validation
- All endpoints extract user ID from `X-User-Id` header
- Ownership checks for all referenced entities
- Constraint validation (max products, personas, prompt length)
- Proper HTTP status codes (400 for invalid input, 403 for unauthorized, 404 for not found)

## Error Handling
- Delegates to GlobalExceptionHandler
- Returns structured error responses with error codes
