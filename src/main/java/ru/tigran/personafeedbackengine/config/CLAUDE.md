# config/

## Purpose
Spring configuration beans for RabbitMQ, Redis caching, HTTP clients, security, and infrastructure setup.
Includes startup validators to ensure critical configuration is present.

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

### OpenApiConfig
- Configures OpenAPI 3.0 documentation for Swagger UI
- Defines JWT Bearer token security scheme (optional usage)
- **Все эндпоинты видны в Swagger UI без авторизации**
- Security scheme доступен опционально для тестирования защищенных эндпоинтов
- Описание API: заголовок, версия, контактная информация

### RestClientConfig
- Configures HTTP timeout: 30 seconds
- Used by AIGatewayService for AI provider API calls (OpenRouter, AgentRouter)
- Implements retry logic for 429 errors

### ApiKeyValidator
- Implements `ApplicationRunner` to validate configuration at startup
- Checks that AI provider API keys are configured via environment variables
- Prevents application start if required keys are missing or contain placeholder values
- Supports multiple AI providers (OpenRouter, AgentRouter)
- Logs validation results

### JwtSecretValidator
- Implements `ApplicationRunner` to validate JWT secret key at startup
- Validates JWT secret key configuration:
  - Minimum length: 32 characters (HMAC-SHA256 requirement)
  - Not null or empty
  - Not using forbidden/default values (e.g., "secret", "changeme", "test")
- Prevents application start with insecure or default JWT secret key
- Logs validation results with key length
- Recommended key generation: `openssl rand -base64 32`

### RedissonConfig
- Configures Redisson client for distributed locking and advanced Redis features
- Auto-detects Redis host, port, and password from Spring Data Redis configuration
- Supports password-protected Redis instances
- Used for distributed locks across multiple application instances
- Timeout: 10 seconds for lock acquisition
- Enables horizontal scaling with synchronized state updates

### Resilience4jConfig
- Configures circuit breaker for AI provider API protection
- Opens after 5 failures within 30 seconds (50% failure rate threshold)
- Remains open for 20 seconds, then attempts half-open state
- Records AIGatewayException and general Exceptions
- Ignores IllegalArgumentException

### ThreadPoolConfig
- Configures separate thread pools for bulkhead pattern
- `personaGenerationExecutor`: 3-10 threads, 50-task queue (prevents persona tasks from blocking feedback)
- `feedbackGenerationExecutor`: 5-20 threads, 100-task queue
- Enables @Async support with graceful shutdown

### HealthCheckConfig
- Provides health indicators for external dependencies
- `aiProviderHealthIndicator`: Checks AI provider availability
- `rabbitMQHealthIndicator`: RabbitMQ status
- `redisHealthIndicator`: Redis status
- Exposed via `/actuator/health`

### WebConfig
- CORS configuration for frontend integration
- Allowed origins configurable via `cors.allowed-origins`
- Supports credentials, preflight caching (1 hour)
- Exposes custom headers: X-Total-Count, X-Page-Number, X-Page-Size
- Request/response logging filter with duration tracking

## Configuration Properties
Referenced from `application.properties`:
- `spring.rabbitmq.*` - RabbitMQ connection
- `spring.data.redis.*` - Redis connection (host, port, password)
- `app.ai.provider` - Selected AI provider ("openrouter" or "agentrouter")
- `app.openrouter.*` - OpenRouter API settings (api-key, model, timeout, retry-delay)
- `app.agentrouter.*` - AgentRouter API settings (api-key, model, timeout, retry-delay)
