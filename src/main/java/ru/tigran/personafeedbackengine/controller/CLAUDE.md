# controller/

## Purpose
REST API endpoints for client interaction with JWT-based authentication.

## Key Classes

### AuthenticationController
REST API for user registration and login with JWT token generation.

- Base path: `/api/v1/auth`
- Public endpoints (no JWT required)

**Methods:**
- **POST /api/v1/auth/register**: User registration
  - Request: `RegisterRequest` (email, password with validation)
  - Response: `AuthenticationResponse` (userId, accessToken, tokenType="Bearer")
  - HTTP Status: 201 Created
  - Validates email uniqueness, password strength (min 8 chars)
  - Throws: ValidationException if email already exists or validation fails
  - **Usage**: Create new user account with email/password credentials

- **POST /api/v1/auth/login**: User login
  - Request: `LoginRequest` (email, password)
  - Response: `AuthenticationResponse` (userId, accessToken, tokenType="Bearer")
  - HTTP Status: 200 OK
  - Validates email/password match, account is active
  - Throws: ValidationException if credentials invalid or account inactive
  - **Usage**: Authenticate existing user and obtain JWT access token

### PersonaController
Triggers persona generation workflow with user isolation via JWT.

- Base path: `/api/v1/personas`
- **Requires JWT authentication** (Authorization: Bearer <token>)

**Methods:**
- **POST /api/v1/personas**: Trigger persona generation
  - Request: `PersonaGenerationRequest` (prompt text)
  - Response: `JobResponse` with persona ID and initial status "GENERATING"
  - HTTP Status: 202 Accepted
  - User ID extracted from JWT token in SecurityContext
  - Validates prompt length, user permissions
  - **Usage**: Start asynchronous persona generation workflow

  **Example Flow:**
  ```
  Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
  POST /api/v1/personas
  {
    "prompt": "A technical product manager focused on DevOps tools..."
  }

  Response (202 Accepted):
  {
    "id": 42,
    "status": "GENERATING"
  }
  ```

### ProductController
REST API для CRUD операций с продуктами.

- Base path: `/api/v1/products`
- **Requires JWT authentication** (Authorization: Bearer <token>)

**Methods:**
- **POST /api/v1/products**: Создать новый продукт
  - Request: `ProductRequest` (name, description)
  - Response: `ProductResponse` (id, name, description)
  - HTTP Status: 201 Created
  - User ID extracted from JWT token in SecurityContext
  - Validates name (required, 1-200 chars), description (optional, max 5000 chars)
  - **Usage**: Create product for feedback collection

- **GET /api/v1/products**: Получить все продукты пользователя
  - Response: `List<ProductResponse>`
  - HTTP Status: 200 OK
  - Returns only non-deleted products owned by current user
  - **Usage**: List all user's products

- **GET /api/v1/products/{productId}**: Получить продукт по ID
  - Path parameter: productId
  - Response: `ProductResponse`
  - HTTP Status: 200 OK
  - Validates ownership and not deleted
  - **Usage**: Get specific product details

- **PUT /api/v1/products/{productId}**: Обновить продукт
  - Path parameter: productId
  - Request: `ProductRequest` (name, description)
  - Response: `ProductResponse`
  - HTTP Status: 200 OK
  - Validates ownership and not deleted
  - **Usage**: Update existing product

- **DELETE /api/v1/products/{productId}**: Удалить продукт (soft delete)
  - Path parameter: productId
  - HTTP Status: 204 No Content
  - Marks product as deleted (preserves feedback history)
  - **Usage**: Remove product from active list

### FeedbackController
Manages feedback session workflows with user isolation via JWT.

- Base path: `/api/v1/feedback-sessions`
- **Requires JWT authentication** (Authorization: Bearer <token>)

**Methods:**
- **POST /api/v1/feedback-sessions**: Trigger feedback session
  - Request: `FeedbackSessionRequest` (productIds, personaIds)
  - Response: `JobResponse` with session ID and initial status "PENDING"
  - HTTP Status: 202 Accepted
  - User ID extracted from JWT token in SecurityContext
  - Validates product/persona ownership, counts, existence
  - **Usage**: Start asynchronous feedback generation for product and persona combinations

  **Example Flow:**
  ```
  Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
  POST /api/v1/feedback-sessions
  {
    "productIds": [1, 2, 3],
    "personaIds": [10, 20]
  }

  Response (202 Accepted):
  {
    "id": 123,
    "status": "PENDING"
  }
  ```

- **GET /api/v1/feedback-sessions/{sessionId}**: Poll for session status and results
  - Path parameter: sessionId
  - Query parameters (optional): `page` (0-based), `size` (per-page count)
  - Response: `FeedbackSessionResponse` (status, feedbackResults[], pagination metadata)
  - HTTP Status: 200 OK
  - User ID extracted from JWT token in SecurityContext
  - Pagination behavior:
    - If page and size provided: Returns paginated results with pageNumber, pageSize, totalCount
    - If no pagination params: Returns all results cached, pagination metadata is null
  - **Usage**: Check feedback generation progress and retrieve results

  **Example Flow:**
  ```
  Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
  GET /api/v1/feedback-sessions/123?page=0&size=10

  Response (200 OK):
  {
    "sessionId": 123,
    "status": "COMPLETED",
    "feedbackResults": [
      {
        "id": 1001,
        "productId": 1,
        "personaId": 10,
        "feedback": "Great product for automation..."
      },
      ...
    ],
    "pagination": {
      "pageNumber": 0,
      "pageSize": 10,
      "totalCount": 15
    }
  }
  ```

## Authentication & Authorization

### JWT Flow
1. User registers via `/api/v1/auth/register` or logs in via `/api/v1/auth/login`
2. Server returns JWT token in `accessToken` field
3. Client includes token in subsequent requests: `Authorization: Bearer <token>`
4. JwtAuthenticationFilter validates token and extracts userId
5. SecurityContext stores userId for controller access

### User Isolation
- All endpoints extract userId from JWT token in SecurityContext
- All queries scoped to extracted userId (product/persona ownership checks)
- Prevents users from accessing other users' data

## Request Validation
- **All endpoints**: JWT token required in Authorization header (except /api/v1/auth/*)
- **RegisterRequest/LoginRequest**: Email format (@Email), password length (@Size min:8, max:128)
- **PersonaGenerationRequest**: Prompt length constraints
- **FeedbackSessionRequest**: Product/persona count limits, existence validation
- **Ownership checks**: All entities verified to belong to authenticated user
- **Proper HTTP status codes**:
  - 201 Created (successful registration)
  - 200 OK (successful login, feedback retrieval)
  - 202 Accepted (async job started)
  - 400 Bad Request (validation failure)
  - 401 Unauthorized (missing/invalid token)
  - 403 Forbidden (user doesn't own entity)
  - 404 Not Found (entity doesn't exist)

## Error Handling
- Delegates to GlobalExceptionHandler
- Returns structured error responses with error codes
- Invalid JWT tokens continue filter chain (don't throw exceptions)
- Missing Authorization header results in null authentication in controller
