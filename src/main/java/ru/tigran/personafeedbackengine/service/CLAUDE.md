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

### PersonaPromptBuilder
- **NEW**: Builder for constructing structured AI prompts for persona generation
- Provides centralized prompt management with clear demographic/psychographic sections
- Automatic age distribution calculation for even spread across specified range
- Integrates seamlessly with PersonaGenerationRequest parameters
- Supports multiple generation scenarios with specialized prompts

**Static Methods:**
- `buildSystemPrompt(int personaCount) -> String`
  - Builds optimized system prompt for batch persona generation
  - Includes CORE REQUIREMENTS, OUTPUT FORMAT, VALIDATION CHECKLIST sections
  - Specifies sampling parameters (temperature 0.7, top_p 0.95, frequency_penalty 0.2, etc.)
  - Returns consistent, battle-tested system prompt for any persona count (1-10)

- `buildUserPrompt(PersonaGenerationRequest request) -> String`
  - **RECOMMENDED**: Builds structured user prompt from PersonaGenerationRequest
  - Automatically extracts all demographics and psychographics from request
  - Calculates age distribution using helper method
  - Returns formatted prompt with DEMOGRAPHICS, PSYCHOGRAPHICS, CRITICAL REQUIREMENTS sections
  - Integrates interests and traits throughout persona descriptions
  - Includes validation checklist specific to requested count

- `calculateAgeDistribution(int minAge, int maxAge, int count) -> String`
  - Helper method for even age distribution
  - Example: minAge=20, maxAge=60, count=5 → "20, 30, 40, 50, 60"
  - For single persona: returns average age (minAge + maxAge) / 2
  - For multiple personas: distributes evenly with equal steps
  - Used internally by buildUserPrompt()

- `buildFeedbackSystemPrompt() -> String`
  - Specialized system prompt for product feedback generation
  - More conservative than persona generation (temperature 0.6)
  - Includes security warnings about <DATA> tags

- `buildThemeAggregationPrompt() -> String`
  - Specialized system prompt for feedback theme aggregation
  - Most conservative settings (temperature 0.5) for logical grouping
  - Format expects JSON array output

**Integration:**
- Used by `AIGatewayService.generatePersonasFromRequest()` for batch generation
- Can be used directly in PersonaService for flexible prompt customization

### AIGatewayService
- Smart multi-provider client for AI API integration (OpenRouter, AgentRouter)
- Supports both **synchronous** (RestClient) and **asynchronous** (WebClient) HTTP calls
- Synchronous calls: HTTP timeout 30s, retry logic for 429 errors only
- Asynchronous calls: HTTP timeout 30s, retry logic for retriable errors (429, 502, 503, 504)
- Dynamic provider selection based on `app.ai.provider` configuration
- Supports any LLM model (Claude, GPT-4o, Mistral, etc.)
- **Consumer research focus**: Generates realistic personas and feedback for market analysis
- **OpenRouter Preset support**: Optimized presets for personas and feedback generation

**Configuration:**
- `app.ai.provider` - Choose between "openrouter" or "agentrouter"
- Each provider has separate API key and model configuration
- Models are independently configurable
- **OpenRouter Presets:**
  - `app.openrouter.preset-personas` - Preset for persona generation (default: `@preset/create-persons`)
  - `app.openrouter.preset-feedback` - Preset for feedback generation (optional, leave empty to not use)

**Synchronous Methods (blocking):**
- **RECOMMENDED**: `generatePersonasFromRequest(Long userId, PersonaGenerationRequest request) -> String`
  - **NEW**: Generates N personas using PersonaPromptBuilder for structured prompts
  - Uses PersonaGenerationRequest for all demographic/psychographic parameters
  - Automatic age distribution calculation and formatting
  - Robust retry mechanism (5 attempts with exponential backoff, 1s→2s→4s→8s→8s max)
  - Returns valid JSON array or throws exception
  - **Advantages**:
    - ✅ Structured prompts with clear demographic/psychographic sections
    - ✅ Automatic age distribution ensures full range coverage
    - ✅ All parameters properly formatted from PersonaGenerationRequest
    - ✅ Same validation as other batch methods
  - **Integration**: Used by `PersonaService.startBatchPersonaGenerationWithPromptBuilder()`

- **BEST**: `generatePersonaWithFixedName(Long userId, String demographicsJson, String psychographicsJson, String fixedName) -> String`
  - **NEW**: Generates a single persona with a FIXED NAME (for guaranteed diversity in batch)
  - **Usage**: Call this 6 times with 6 different names from PersonaName enum for guaranteed diversity
  - **AI Constraint**: Fixed name is embedded in system prompt - AI MUST use this exact name
  - **Returns**: JSON object with persona using the fixed name
  - **Advantages**:
    - ✅ GUARANTEES DIVERSITY: Different names → completely different personas
    - ✅ NAME-DRIVEN: Name determines personality, background, values
    - ✅ SIMPLE: No complex retry logic needed (just call it N times in parallel)
    - ✅ PARALLEL-FRIENDLY: Each call is independent, can run in parallel
    - ✅ FLEXIBLE: Works with male/female names from PersonaName enum
  - **Integration**: Used by `PersonaService.startBatchPersonaGenerationWithFixedNames()` in parallel via CompletableFuture

- `generateMultiplePersonasWithRetry(Long userId, String demographicsJson, int personaCount) -> String`
  - Generates N distinct personas in a SINGLE AI call
  - **Robust retry mechanism**: Up to 5 retry attempts with exponential backoff (1s, 2s, 4s, 8s, 8s max)
  - **Intelligent error feedback**: If JSON parsing fails, sends explicit error details to AI for correction
  - **Guaranteed to succeed**: Returns valid JSON array or throws exception
  - **Returns**: JSON array with persona objects: `[{"name": "...", "age": 28, "profession": "...", ...}, ...]`
  - **Advantages**:
    - ✅ 1 API call instead of 6 → 6x faster and cheaper
    - ✅ Strict validation: each persona must have all 9 required fields
    - ✅ Exponential backoff prevents overwhelming AI
  - **Disadvantage**: ❌ Less diversity guarantee than fixed names approach

- `generatePersonaDetails(Long userId, String demographicsJson, String psychographicsJson)`:
  - Single persona generation (legacy, not recommended for batch)
  - **NOT CACHED** - Each call produces a unique persona
  - **Variant-based diversity**: Extracts `variant_number` from psychographicsJson JSON
    - If `variant_number` present, includes explicit diversity instruction in system prompt
    - AI instructed to generate DISTINCTLY DIFFERENT persona from others in batch
  - **Output ALWAYS in English** for consistency
  - Returns JSON with required and optional fields:
    - **Required**: `name`, `detailed_bio`, `product_attitudes`
    - **Optional**: `gender`, `age_group`, `race`
- `generateFeedbackForProduct(personaBio, personaProductAttitudes, productName, productDescription, productPrice, productCategory, productKeyFeatures, languageCode)`:
  - Not cached (volatile, session-specific)
  - Analyzes product based on persona's shopping habits and values
  - Returns structured JSON: `{feedback (in languageCode), purchase_intent (1-10), key_concerns (array)}`
  - Language code is ISO 639-1 format (EN, RU, FR, etc.)
- `aggregateKeyThemes(List<String> allConcerns)`:
  - Groups similar concerns from all feedback results into key themes
  - Uses AI to identify patterns and count mentions
  - Returns JSON array: `[{"theme": "...", "mentions": N}, ...]`
  - Returns top 5-7 most important themes

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

**OpenRouter Preset Integration:**
- `private String callAIProvider(String systemPrompt, String userMessage, String usePreset)`
  - Core method with OpenRouter preset support
  - Parameter `usePreset` can be: "personas", "feedback", or null
  - Automatically includes preset in request body if OpenRouter provider is configured
  - Logs which preset is being used for debugging
- `private String callAIProvider(String systemPrompt, String userMessage)` (legacy)
  - Delegates to main method with usePreset=null
  - Maintains backward compatibility with existing code

**Preset Usage in Methods:**
- `generatePersonasFromRequest()` - Uses preset "personas" (default: @preset/create-persons)
  - Optimized for batch persona generation with PersonaPromptBuilder
  - Automatically applies preset if configured in OpenRouter
  - Comprehensive logging: tracks outer loop attempts (5 max), inner loop attempts (3 max)
  - Logs all API requests/responses with request/response preview in INFO level
- `generateFeedbackForProduct()` - Uses preset "feedback" (optional, if configured)
  - Optimized for product feedback generation
  - Only applies preset if openrouter.preset-feedback is configured and not empty

**Request Body Generation:**
- `buildRequestBody()` method automatically:
  1. Checks if provider is OpenRouter
  2. Checks if usePreset is specified
  3. Validates that preset configuration exists and is not empty
  4. Adds "preset" field to JSON request body if conditions are met
  5. Logs the preset being used for debugging

**Logging & Debugging:**
- **Detailed request/response logging**: INFO level logs show:
  - generatePersonasFromRequest outer loop attempt number (e.g., "attempt 1/5")
  - callAIProvider inner loop attempt number (e.g., "API call attempt 1/3")
  - System prompt and user message (first 500 chars preview)
  - AI response content (first 800 chars preview)
  - Preset being used (or "NONE")
- **Error logging**: extractMessageContent() logs:
  - Response root keys when content extraction fails
  - Response preview (first 200 chars)
  - Helps diagnose "Expected JSON array, got MISSING" errors
- **Validation logging**: validatePersonasArray() logs:
  - Array size and node type
  - Detailed error messages with actual vs expected count

**Async Implementation Details:**
- Uses Spring WebFlux WebClient with Reactor Netty
- Retry mechanism for retriable errors (429, 502, 503, 504) with exponential backoff
- Timeout: 30 seconds per request
- Max retries: 3 attempts
- Non-blocking: does not block thread during network I/O

### PersonaService
- Entry point for persona generation workflow with **four approaches**

#### RECOMMENDED: `startBatchPersonaGenerationWithPromptBuilder(Long userId, PersonaGenerationRequest request) -> List<Long>` ⭐⭐
- **NEW**: Batch persona generation with PersonaPromptBuilder for structured prompts
- **BEST OVERALL**: Combines structured prompts with robust retry mechanism
- Uses `PersonaPromptBuilder.buildSystemPrompt()` and `PersonaPromptBuilder.buildUserPrompt()`
- Calls `AIGatewayService.generatePersonasFromRequest()` (with 5 retry attempts)
- Generates all N personas in ONE AI call with clear demographic/psychographic sections
- **Advantages**:
  - ✅ Structured prompts with clear sections (DEMOGRAPHICS, PSYCHOGRAPHICS, REQUIREMENTS)
  - ✅ Automatic age distribution ensures full range coverage
  - ✅ All parameters properly formatted from PersonaGenerationRequest
  - ✅ 1 API call (cheapest, fastest wall-clock time)
  - ✅ Robust retry with exponential backoff
  - ✅ 100% guaranteed to succeed or throw exception
  - ✅ Flexible: Works with any PersonaGenerationRequest configuration
- **Disadvantage**: ❌ Slightly less diversity guarantee than fixed names approach
- **Integration**: Uses PersonaPromptBuilder for centralized prompt management

#### BEST: `startBatchPersonaGenerationWithFixedNames(Long userId, PersonaGenerationRequest request) -> List<Long>` ⭐
- **NEW**: Batch persona generation with FIXED NAMES in PARALLEL
- **GUARANTEES DIVERSITY**: Uses predefined male/female names from database, organized by country
- Flow:
  1. Selects N unique random names from `names` table (country-specific if available)
  2. Starts N CompletableFuture parallel tasks
  3. Each task calls `AIGatewayService.generatePersonaWithFixedName()` with a specific name
  4. Waits for all tasks to complete using `CompletableFuture.allOf()`
  5. Parses all responses
  6. Creates Persona entities with ACTIVE status
  7. Saves all to DB and explicitly flushes
  8. Returns list of persona IDs
- **Name Selection Strategy** (in `selectRandomNames()`):
  1. **First priority**: Try to get N names from database for specified country + gender
     - E.g., if country=RU and gender=FEMALE → gets Russian female names (Marina, Natalia, etc.)
     - Ensures culturally appropriate names match demographics
  2. **Fallback 1**: If country not found in DB → get random names of specified gender (any country)
  3. **Fallback 2**: If database is empty → use in-memory PersonaName enum
- **Advantages**:
  - ✅ GUARANTEED 100% DIVERSITY: Different names → completely different personas (no Volkovs!)
  - ✅ CULTURALLY APPROPRIATE: Russian Ольга + Russian demographics (not Nigerian!)
  - ✅ PARALLEL EXECUTION: 6 requests run simultaneously → very fast
  - ✅ FLEXIBLE: Automatically adjusts names based on country
  - ✅ ROBUST: Each request can fail independently without affecting others
  - ✅ SIMPLE: Fixed names remove AI uncertainty about name generation
  - ✅ REALISTIC: Name-driven persona generation (name determines entire character)
- **Database**: Uses `NameRepository` to fetch names from `names` table
  - 400 predefined names: 10 male + 10 female for 20 most popular countries
  - Countries: RU, US, GB, DE, FR, IT, ES, BR, CN, IN, JP, KR, MX, CA, AU, NL, SE, PL, TR, NG
- **Helper methods**: `selectRandomNames(gender, country, count)`, `parsePersonaData()`
- **Helper record**: `PersonaData` - stores parsed persona from AI

#### ALTERNATIVE: `startBatchPersonaGeneration(Long userId, PersonaGenerationRequest request) -> List<Long>`
- Batch persona generation with single AI call + robust retry logic
- Calls `AIGatewayService.generateMultiplePersonasWithRetry()` (with 5 retry attempts)
- Generates all 6 personas in ONE AI request
- **Advantages**:
  - ✅ 1 API call (cheapest, fastest wall-clock time)
  - ✅ Robust retry with explicit validation feedback
  - ✅ 100% guaranteed to succeed or throw exception
- **Disadvantage**: ❌ Less guaranteed diversity than fixed names approach

#### Legacy: `startPersonaGeneration(Long userId, PersonaGenerationRequest request)`
- Creates N persona entities in GENERATING state
- Uses **two-phase approach** with explicit flush before publishing tasks
- Publishes PersonaGenerationTasks to RabbitMQ queue for async processing
- Each persona gets variant_number in psychographics for diversity
- Returns ID of first created persona

#### `findOrCreatePersonas(Long userId, List<PersonaVariation> variations) -> List<Long>`
- **Two-phase approach**: Find/create all, flush, then publish tasks
- For each variation: searches DB by demographics (gender, age, region, incomeLevel)
- If found ACTIVE persona - reuses it (no new task published)
- If not found - creates new persona (without publishing) and stores in list
- After creating all: calls `personaRepository.flush()`
- Then publishes all tasks with variant_number included
- Returns mix of existing and newly created persona IDs
- **Helper record**: `PersonaGenerationInfo(personaId, task, variantNumber)` - stores for two-phase approach

### PersonaVariationService
- Generates demographic variations from target audience parameters
- `generateVariations(TargetAudience targetAudience, int personaCount) -> List<PersonaVariation>`:
  - Takes structured demographics (genders, ageRanges, regions, incomes)
  - Generates N specific persona variations using round-robin distribution
  - Converts age ranges to specific ages (e.g., "25-35" -> random age 25-35)
  - Supports formats: "18-25", "26-35", "36-45", "46+"
  - Returns list of PersonaVariation with specific demographics for each persona

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
- Entry point for feedback session workflow with two operating modes
- `startFeedbackSession(Long userId, FeedbackSessionRequest request) -> Long`:
  - **Mode 1 (Explicit personas)**: Uses provided personaIds list
  - **Mode 2 (Auto-generate)**: Uses targetAudience + personaCount
    - Calls PersonaVariationService to generate variations
    - Calls PersonaService.findOrCreatePersonas to get/create personas
    - Waits for newly created personas to become ACTIVE (polling, 60s timeout)
  - Creates FeedbackSession + FeedbackResult entities for all product-persona pairs
  - Publishes FeedbackGenerationTasks to queue
  - Returns session ID
- Validates request ownership, product/persona existence, and constraints
- `waitForPersonasReady(userId, personaIds, timeoutSec)`: Polls persona status until all ACTIVE

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
- **Responsibility**: Core persona generation business logic with race condition prevention
- Extracted from PersonaTaskConsumer to follow Single Responsibility Principle
- Called by PersonaTaskConsumer to handle the actual generation workflow
- **Flexible validation**: Validates only required fields (name, detailed_bio, product_attitudes) from AI
- **Race condition fix (CRITICAL-3)**: Prevents concurrent updates from multiple RabbitMQ consumer threads
  - Uses `generationInProgress` boolean flag on Persona entity
  - Flag is set/cleared in separate transactions (REQUIRES_NEW propagation) to prevent optimistic locking conflicts
  - When multiple consumers pick up the same persona task, only the first one processes it; others skip
  - This prevents `StaleObjectStateException` caused by version conflicts when multiple threads update same entity
- **Methods:**
  - `generatePersona(PersonaGenerationTask task)`: Orchestrates persona generation with race condition prevention
    1. Fetch Persona entity and validate state (idempotency check)
    2. Check if `generationInProgress` flag is set; skip if true (another thread is processing)
    3. Mark persona as `generationInProgress = true` in separate transaction
    4. Call AIGatewayService.generatePersonaDetails(userId, demographicsJson, psychographicsJson)
    5. Parse and validate JSON response (checks only required fields)
    6. Update Persona entity with generated details (name, detailed_bio, product_attitudes; optional: gender, ageGroup, race)
    7. Persist changes to database
    8. Clear `generationInProgress = false` in finally block (separate transaction, always executes)
  - `markGenerationInProgress(Long personaId)`: Sets flag in separate transaction (REQUIRES_NEW)
  - `clearGenerationInProgress(Long personaId)`: Clears flag in separate transaction (REQUIRES_NEW), logs errors but doesn't throw
- **Private methods:**
  - `parsePersonaDetails(String json)`: Parses JSON response from AI
  - `validatePersonaDetails(JsonNode details)`: Validates only required fields: name, detailed_bio, product_attitudes
  - `updatePersonaEntity(Persona persona, JsonNode details)`: Maps JSON fields to entity properties
    - Required: name → name, detailed_bio → detailedDescription, product_attitudes → productAttitudes
    - Optional: gender → gender, age_group → ageGroup, race → race (only set if present in AI response)

### FeedbackGenerationService
- **Responsibility**: Core feedback generation business logic, session completion, and insights aggregation
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
    - **When session complete**: calls aggregateSessionInsights and saves to session
    - Updates session status to COMPLETED
    - Handles InterruptedException and general exceptions
  - `aggregateSessionInsights(Long sessionId) -> String`: Generates aggregated insights
    - Loads all COMPLETED feedback results
    - Calculates averageScore (mean purchase_intent)
    - Calculates purchaseIntentPercent (% with intent >= 7)
    - Collects all keyConcerns from all results
    - Calls AIGatewayService.aggregateKeyThemes to group themes
    - Returns AggregatedInsights as JSON string
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
