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
- **GET /api/v1/feedback-sessions/{sessionId}**: Poll for session status/results with optional pagination
  - Query parameters (optional): `page` (0-based page number), `size` (page size)
  - Response: `FeedbackSessionResponse` with nested FeedbackResults and pagination metadata
  - If page/size provided: returns paginated results with pageNumber, pageSize, totalCount
  - If no pagination params: returns all results cached, pagination metadata is null

## Request Validation
- All endpoints extract user ID from `X-User-Id` header
- Ownership checks for all referenced entities
- Constraint validation (max products, personas, prompt length)
- Proper HTTP status codes (400 for invalid input, 403 for unauthorized, 404 for not found)

## Error Handling
- Delegates to GlobalExceptionHandler
- Returns structured error responses with error codes
