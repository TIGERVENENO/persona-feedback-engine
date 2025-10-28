# service/

## Purpose
Business logic and orchestration layer for the application.

## Key Classes

### AuthenticationService
User registration and login service with JWT token generation and BCrypt password hashing.

**Methods:**
- `register(RegisterRequest request) → AuthenticationResponse`
  - Creates new user with email and hashed password
  - Validates email is unique before insertion
  - Uses BCryptPasswordEncoder to hash password securely
  - Generates JWT token with user ID as subject
  - Returns userId, accessToken, tokenType="Bearer"
  - Throws ValidationException if email already exists or validation fails
  - Transactional with readOnly=false

- `login(LoginRequest request) → AuthenticationResponse`
  - Finds user by email address
  - Verifies user account is active (not deleted)
  - Matches provided password against stored BCrypt hash
  - Generates JWT token with user ID as subject
  - Returns userId, accessToken, tokenType="Bearer"
  - Throws ValidationException if email not found, account inactive, or password invalid
  - Transactional with readOnly=true

**Integration:**
- Uses UserRepository for database access (findByEmail, existsByEmail)
- Uses PasswordEncoder (BCryptPasswordEncoder) for password hashing and verification
- Uses JwtTokenProvider for token generation
- Called by AuthenticationController for register/login endpoints

**Key Implementation Details:**
- Password validation uses passwordEncoder.matches() for constant-time comparison
- Email uniqueness checked via existsByEmail() before registration
- Both methods log authentication attempts at info level
- Errors include descriptive messages (INVALID_CREDENTIALS, EMAIL_ALREADY_EXISTS)

### AIGatewayService
- Smart multi-provider client for AI API integration (OpenRouter, AgentRouter)
- Supports both **synchronous** (RestClient) and **asynchronous** (WebClient) HTTP calls
- Synchronous calls: HTTP timeout 30s, retry logic for 429 errors only
- Asynchronous calls: HTTP timeout 30s, retry logic for retriable errors (429, 502, 503, 504)
- Dynamic provider selection based on `app.ai.provider` configuration
- Supports any LLM model (Claude, GPT-4o, Mistral, etc.)
- **Consumer research focus**: Generates realistic personas and feedback for market analysis

**Configuration:**
- `app.ai.provider` - Choose between "openrouter" or "agentrouter"
- Each provider has separate API key and model configuration
- Models are independently configurable

**Synchronous Methods (blocking):**
- `generatePersonaDetails(Long userId, String demographicsJson, String psychographicsJson)`:
  - Cacheable (by userId + demographics + psychographics)
  - Generates detailed persona profile with consumer behavior focus
  - **Output ALWAYS in English** for consistency
  - Returns JSON: `{name, detailed_bio (150-200 words), product_attitudes}`
  - Focuses on shopping habits, brand preferences, decision-making style
- `generateFeedbackForProduct(personaBio, personaProductAttitudes, productName, productDescription, productPrice, productCategory, productKeyFeatures, languageCode)`:
  - Not cached (volatile, session-specific)
  - Analyzes product based on persona's shopping habits and values
  - Returns structured JSON: `{feedback (in languageCode), purchase_intent (1-10), key_concerns (array)}`
  - Language code is ISO 639-1 format (EN, RU, FR, etc.)

**Asynchronous Methods (non-blocking, returns Mono):**
- `generatePersonaDetailsAsync(Long userId, String demographicsJson, String psychographicsJson)`:
  - Async persona generation with same output format as sync version
  - **Output ALWAYS in English**
- `generateFeedbackForProductAsync(personaBio, personaProductAttitudes, productName, productDescription, productPrice, productCategory, productKeyFeatures, languageCode)`:
  - Async feedback generation with structured output

**Response Processing:**
- Automatically cleans markdown code blocks from AI responses (```json ... ```)
- Robust parsing handles cases where AI ignores "no markdown" instructions
- Applies `cleanMarkdownCodeBlocks()` method to all extracted content before validation

**Async Implementation Details:**
- Uses Spring WebFlux WebClient with Reactor Netty
- Retry mechanism for retriable errors (429, 502, 503, 504) with exponential backoff
- Timeout: 30 seconds per request
- Max retries: 3 attempts
- Non-blocking: does not block thread during network I/O

### PersonaService
- Entry point for persona generation workflow
- `startPersonaGeneration(Long userId, PersonaGenerationRequest request)`:
  - Serializes demographics and psychographics to JSON strings
  - Creates Persona entity (state: GENERATING)
  - Publishes PersonaGenerationTask to queue with demographicsJson and psychographicsJson
  - Stores combined JSON as generationPrompt for cache key purposes
- Validates user existence

### ProductService
- CRUD operations for products with user ownership validation
- **Methods:**
  - `createProduct(Long userId, ProductRequest request) → ProductResponse`: Creates new product with price, category, keyFeatures
  - `getProduct(Long userId, Long productId) → ProductResponse`: Gets product by ID with ownership check
  - `getAllProducts(Long userId) → List<ProductResponse>`: Gets all non-deleted products for user
  - `updateProduct(Long userId, Long productId, ProductRequest request) → ProductResponse`: Updates existing product including new fields
  - `deleteProduct(Long userId, Long productId)`: Soft deletes product (sets deleted=true)
- All operations validate user ownership before executing
- Throws ValidationException if product not found or access denied

### FeedbackService
- Entry point for feedback session workflow
- `startFeedbackSession(FeedbackSessionRequest request)`: Creates FeedbackSession + FeedbackResult entities and publishes FeedbackGenerationTasks
- Validates request ownership, product/persona existence, and constraints

### IdempotencyService
- Protects against duplicate requests using idempotency keys
- Stores idempotency keys in Redis with 5-minute TTL
- First request with unique key succeeds, subsequent duplicates are rejected
- Used to prevent accidental double-submission of operations

### SagaService
- Implements Saga pattern for distributed transactions
- Executes series of operations with automatic rollback on failure
- Compensating actions executed in reverse order if any operation fails
- Useful for complex multi-step operations where atomicity across services is needed

### PersonaGenerationService
- **Responsibility**: Core persona generation business logic
- Extracted from PersonaTaskConsumer to follow Single Responsibility Principle
- Called by PersonaTaskConsumer to handle the actual generation workflow
- **Methods:**
  - `generatePersona(PersonaGenerationTask task)`: Orchestrates persona generation
    1. Fetch Persona entity and validate state (idempotency check)
    2. Call AIGatewayService.generatePersonaDetails(userId, demographicsJson, psychographicsJson)
    3. Parse and validate JSON response
    4. Update Persona entity with generated details (name, detailedDescription, productAttitudes)
    5. Persist changes to database
- **Private methods:**
  - `parsePersonaDetails(String json)`: Parses JSON response from AI
  - `validatePersonaDetails(JsonNode details)`: Validates required fields (name, detailed_bio, product_attitudes)
  - `updatePersonaEntity(Persona persona, JsonNode details)`: Maps JSON fields to entity properties
    - name → name
    - detailed_bio → detailedDescription
    - product_attitudes → productAttitudes

### FeedbackGenerationService
- **Responsibility**: Core feedback generation business logic and session completion
- Extracted from FeedbackTaskConsumer to follow Single Responsibility Principle
- Called by FeedbackTaskConsumer to handle the actual generation workflow
- Uses Redisson distributed locking for safe session status updates across instances
- **Methods:**
  - `generateFeedback(FeedbackGenerationTask task)`: Orchestrates feedback generation
    1. Fetch FeedbackResult, Persona, Product entities
    2. Check idempotency (skip if COMPLETED)
    3. Mark result as IN_PROGRESS
    4. Call AIGatewayService.generateFeedbackForProduct() with:
       - persona.detailedDescription (bio)
       - persona.productAttitudes
       - product.name, description, price, category, keyFeatures
       - task.language
    5. Parse JSON response (feedback, purchase_intent, key_concerns)
    6. Validate purchase_intent in range 1-10, key_concerns is array
    7. Update FeedbackResult with all fields
    8. Check and update session completion status
  - `checkAndUpdateSessionCompletion(Long sessionId)`: Atomically updates session status
    - Uses distributed lock (Redisson) with 10-second timeout
    - Checks if all results for session are done (completed + failed >= total)
    - Updates session status to COMPLETED if all results done
    - Handles InterruptedException and general exceptions
- **Private methods:**
  - `parseFeedbackResponse(String json)`: Parses JSON response from AI
  - `validateFeedbackResponse(JsonNode data)`: Validates required fields (feedback, purchase_intent, key_concerns)
  - `extractKeyConcerns(JsonNode node)`: Extracts concerns array from JSON

## Responsibilities
- Input validation and ownership checks
- Entity creation and state management
- Message queue publishing
- Ownership-based data access control
- Duplicate request prevention (idempotency)
- Distributed transaction management (Saga pattern)
- AI-driven generation logic (PersonaGenerationService, FeedbackGenerationService)
- JSON parsing and validation
- Distributed locking for atomic operations
