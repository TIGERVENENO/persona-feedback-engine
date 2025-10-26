# config/

## Purpose
Spring configuration beans for RabbitMQ, Redis caching, HTTP clients, and other infrastructure.

## Key Classes

### RabbitMQConfig
- Defines message queues and exchanges with Dead Letter Queue (DLQ) support:
  - **Main queues:**
    - `persona.generation.queue` - Heavy persona generation tasks
    - `feedback.generation.queue` - Feedback generation tasks
  - **Dead Letter Queues (for failed messages):**
    - `persona.generation.dlq` - Failed persona generation tasks
    - `feedback.generation.dlq` - Failed feedback generation tasks
  - **Exchanges:**
    - `persona-feedback-exchange` - Main exchange for task distribution
    - `persona-feedback-dlx` - Dead Letter Exchange for failed messages
- Automatic routing: Failed/rejected messages are routed to DLQ for later analysis
- Enables message routing, deduplication, and failure tracking

### CacheConfig
- Configures Redis as the cache manager
- Creates a specific cache named `personaCache`
- TTL: 24 hours (86400 seconds)
- Caching strategy: By generation prompt for persona reusability

### RestClientConfig
- Configures HTTP timeout: 30 seconds
- Used by AIGatewayService for AI provider API calls (OpenRouter, AgentRouter)
- Implements retry logic for 429 errors

## Configuration Properties
Referenced from `application.properties`:
- `spring.rabbitmq.*` - RabbitMQ connection
- `spring.data.redis.*` - Redis connection
- `app.ai.provider` - Selected AI provider ("openrouter" or "agentrouter")
- `app.openrouter.*` - OpenRouter API settings (api-key, model, timeout, retry-delay)
- `app.agentrouter.*` - AgentRouter API settings (api-key, model, timeout, retry-delay)
