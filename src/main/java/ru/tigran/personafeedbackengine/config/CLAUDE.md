# config/

## Purpose
Spring configuration beans for RabbitMQ, Redis caching, HTTP clients, and other infrastructure.

## Key Classes

### RabbitMQConfig
- Defines two separate queues:
  - `persona.generation.queue` - Heavy persona generation tasks
  - `feedback.generation.queue` - Feedback generation tasks
- Configures queue-to-exchange bindings
- Enables message routing and deduplication

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
