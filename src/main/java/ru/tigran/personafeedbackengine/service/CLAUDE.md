# service/

## Purpose
Business logic and orchestration layer for the application.

## Key Classes

### AIGatewayService
- Smart multi-provider client for AI API integration (OpenRouter, AgentRouter)
- Supports both **synchronous** (RestClient) and **asynchronous** (WebClient) HTTP calls
- Synchronous calls: HTTP timeout 30s, retry logic for 429 errors only
- Asynchronous calls: HTTP timeout 30s, retry logic for retriable errors (429, 502, 503, 504)
- Token optimization via abbreviated JSON keys
- Dynamic provider selection based on `app.ai.provider` configuration
- Supports any LLM model (Claude, GPT-4o, Mistral, etc.)

**Configuration:**
- `app.ai.provider` - Choose between "openrouter" or "agentrouter"
- Each provider has separate API key and model configuration
- Models are independently configurable

**Synchronous Methods (blocking):**
- `generatePersonaDetails(Long userId, String userPrompt)`: Cacheable, generates detailed persona profile (cached by userId + prompt)
- `generateFeedbackForProduct(String personaDescription, String productDescription)`: Generates feedback text (not cached)

**Asynchronous Methods (non-blocking, returns Mono):**
- `generatePersonaDetailsAsync(Long userId, String userPrompt)`: Async persona generation with non-blocking retry logic
- `generateFeedbackForProductAsync(String personaDescription, String productDescription)`: Async feedback generation

**Async Implementation Details:**
- Uses Spring WebFlux WebClient with Reactor Netty
- Retry mechanism for retriable errors (429, 502, 503, 504) with exponential backoff
- Timeout: 30 seconds per request
- Max retries: 3 attempts
- Non-blocking: does not block thread during network I/O

### PersonaService
- Entry point for persona generation workflow
- `startPersonaGeneration(PersonaGenerationRequest request)`: Creates Persona entity (state: GENERATING) and publishes PersonaGenerationTask to queue
- Validates request ownership and constraints

### FeedbackService
- Entry point for feedback session workflow
- `startFeedbackSession(FeedbackSessionRequest request)`: Creates FeedbackSession + FeedbackResult entities and publishes FeedbackGenerationTasks
- Validates request ownership, product/persona existence, and constraints

## Responsibilities
- Input validation and ownership checks
- Entity creation and state management
- Message queue publishing
- Ownership-based data access control
