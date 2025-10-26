# service/

## Purpose
Business logic and orchestration layer for the application.

## Key Classes

### AIGatewayService
- Smart multi-provider client for AI API integration (OpenRouter, AgentRouter)
- Uses Spring's RestClient (HTTP timeout: 30s)
- Implements simple retry mechanism for 429 errors (rate limit)
- Token optimization via abbreviated JSON keys
- Dynamic provider selection based on `app.ai.provider` configuration
- Supports any LLM model (Claude, GPT-4o, Mistral, etc.)

**Configuration:**
- `app.ai.provider` - Choose between "openrouter" or "agentrouter"
- Each provider has separate API key and model configuration
- Models are independently configurable

**Methods:**
- `generatePersonaDetails(Long userId, String userPrompt)`: Cacheable, generates detailed persona profile (cached by userId + prompt for data isolation)
- `generateFeedbackForProduct(String personaDescription, String productDescription)`: Generates feedback text (not cached, user-specific)

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
