# Persona Feedback Engine MVP

A robust, scalable Spring Boot 3.5.7 backend service for generating AI personas and simulating product feedback. Built as a modular monolith with asynchronous processing via RabbitMQ.

## Overview

The Persona Feedback Engine allows marketers to:
1. **Generate AI Personas** - Create detailed, realistic customer profiles using any LLM model
2. **Simulate Feedback** - Get product feedback from multiple personas simultaneously
3. **Manage Sessions** - Track feedback generation progress and access results
4. **Choose AI Provider & Model** - Switch between providers (OpenRouter, AgentRouter) and models without code changes

The system uses a two-stage async workflow:
- **Stage 1**: Heavy persona generation (cacheable by prompt)
- **Stage 2**: Lightweight feedback generation using generated personas

All processing is decoupled via RabbitMQ to keep the API responsive.

## Quick Start

Get the entire project running in 3 minutes:

```bash
# 1. Clone the repository
git clone <repository-url>
cd persona-feedback-engine

# 2. Create configuration from template
cp .env.example .env

# 3. Edit .env and set your AI Provider API key
#    OPENROUTER_API_KEY=your_key_here (for OpenRouter)
#    AGENTROUTER_API_KEY=your_key_here (for AgentRouter)

# 4. Start infrastructure (PostgreSQL, Redis, RabbitMQ)
docker-compose up -d

# 5. Build the Spring Boot application
mvn clean package

# 6. Run the application (in another terminal)
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=docker"

# 7. Application is ready at http://localhost:8080
```

**Verify services are running:**
```bash
docker-compose ps
# Should show: postgres, redis, rabbitmq - all with status "healthy"
```

## Prerequisites

### Required

- **Java 21** - [Download JDK 21](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
- **Maven 3.8+** - [Download Maven](https://maven.apache.org/download.cgi)
- **Docker Desktop** - [Download Docker](https://www.docker.com/products/docker-desktop/)
- **AI Provider API Key** - Choose one:
  - **OpenRouter** - [Get free credits](https://openrouter.ai/keys)
  - **AgentRouter** - [Get free credits](https://agentrouter.ai/keys)

### Optional

- **PostgreSQL Client** (psql) - for direct database access
- **Redis CLI** - for Redis management
- **curl** or **Postman** - for API testing

## Setup Instructions

### Step 1: Configure Environment

```bash
# Copy the example environment file
cp .env.example .env

# Edit .env with your credentials
# Most important: set OPENROUTER_API_KEY
nano .env  # or your preferred editor
```

**Key environment variables:**
- `AI_PROVIDER` - Which AI provider to use: `openrouter` or `agentrouter` (default: `agentrouter`)
- `OPENROUTER_API_KEY` - Your OpenRouter API key (required if using OpenRouter)
- `AGENTROUTER_API_KEY` - Your AgentRouter API key (required if using AgentRouter)
- `POSTGRES_PASSWORD` - Database password (default: postgres)
- `REDIS_PASSWORD` - Redis password (default: redispass)
- `RABBITMQ_PASSWORD` - RabbitMQ password (default: guest)

### Step 2: Start Docker Services

```bash
# Start all services in background
docker-compose up -d

# Verify all services are running and healthy
docker-compose ps

# View logs (useful for debugging)
docker-compose logs -f

# Stop services (without removing data)
docker-compose stop

# Remove containers and volumes (WARNING: deletes all data)
docker-compose down -v
```

**Expected output from `docker-compose ps`:**
```
NAME                        STATUS      PORTS
persona-feedback-postgres   healthy     0.0.0.0:5432->5432/tcp
persona-feedback-redis      healthy     0.0.0.0:6379->6379/tcp
persona-feedback-rabbitmq   healthy     0.0.0.0:5672->5672/tcp, 0.0.0.0:15672->15672/tcp
```

### Step 3: Build Spring Boot Application

```bash
# Clean and package the application
mvn clean package

# This creates target/persona-feedback-engine-0.0.1-SNAPSHOT.jar
# It also runs all unit tests and integration tests

# Skip tests if needed (not recommended)
mvn clean package -DskipTests
```

### Step 4: Run the Application

```bash
# Run with Docker profile enabled
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=docker"

# Or directly run the JAR
java -Dspring.profiles.active=docker -jar target/persona-feedback-engine-0.0.1-SNAPSHOT.jar

# Application starts on http://localhost:8080
```

**You should see logs like:**
```
Started PersonaFeedbackEngineApplication in 5.234 seconds
Tomcat initialized with port(s): 8080 (http)
HikariPool-1 - Starting - connections: 10
```

### Step 5: Verify Application is Running

```bash
# Simple health check
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP"}
```

## API Endpoints

### 1. Generate AI Persona

**Endpoint:** `POST /api/v1/personas`

**Description:** Triggers asynchronous persona generation. Returns immediately with a job ID.

**Headers:**
```
X-User-Id: 1
Content-Type: application/json
```

**Request Body:**
```json
{
  "prompt": "Create a tech-savvy millennial marketing manager who values innovation and UX design"
}
```

**Example with curl:**
```bash
curl -X POST http://localhost:8080/api/v1/personas \
  -H "X-User-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Tech-savvy marketing manager in her late 20s who loves startups and enjoys coffee"
  }'
```

**Response (HTTP 202 - Accepted):**
```json
{
  "jobId": 1,
  "status": "GENERATING"
}
```

**What happens next:**
- Persona record is created in GENERATING state
- Task is published to RabbitMQ queue
- Consumer processes the task asynchronously
- AI generates detailed persona (name, gender, age group, interests)
- Persona state updates to ACTIVE when complete
- Persona is cached by prompt for reuse in future sessions

---

### 2. Create Feedback Session

**Endpoint:** `POST /api/v1/feedback-sessions`

**Description:** Creates a feedback session and generates feedback from specified personas on specified products.

**Headers:**
```
X-User-Id: 1
Content-Type: application/json
```

**Request Body:**
```json
{
  "productIds": [1, 2],
  "personaIds": [1]
}
```

**Example with curl:**
```bash
curl -X POST http://localhost:8080/api/v1/feedback-sessions \
  -H "X-User-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "productIds": [1, 2],
    "personaIds": [1]
  }'
```

**Response (HTTP 202 - Accepted):**
```json
{
  "jobId": 1,
  "status": "PENDING"
}
```

**What happens next:**
- FeedbackSession created with status PENDING
- FeedbackResult records created for each (product, persona) pair
  - In this example: 2 products × 1 persona = 2 feedback results
- Tasks published to RabbitMQ for asynchronous processing
- Consumers fetch each persona and product details
- AI generates feedback text from each persona's perspective
- FeedbackResults update with feedback text and status changes to COMPLETED
- FeedbackSession status updates to COMPLETED when all results are done

---

### 3. Poll Feedback Session Status

**Endpoint:** `GET /api/v1/feedback-sessions/{sessionId}`

**Description:** Retrieves the current status and all feedback results for a session.

**Headers:**
```
X-User-Id: 1
```

**Example with curl:**
```bash
curl -X GET http://localhost:8080/api/v1/feedback-sessions/1 \
  -H "X-User-Id: 1"
```

**Response (HTTP 200):**
```json
{
  "id": 1,
  "status": "COMPLETED",
  "createdAt": "2024-10-24T10:30:00",
  "feedbackResults": [
    {
      "id": 1,
      "feedbackText": "This coffee maker is amazing! The WiFi connectivity lets me schedule brewing from my phone...",
      "status": "COMPLETED",
      "personaId": 1,
      "productId": 1
    },
    {
      "id": 2,
      "feedbackText": "Great design, but I wish the noise-canceling was stronger in loud office environments...",
      "status": "COMPLETED",
      "personaId": 1,
      "productId": 2
    }
  ]
}
```

---

### 4. Full Example Workflow

```bash
# Step 1: Generate a persona
PERSONA_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/personas \
  -H "X-User-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "A 32-year-old software engineer who loves gadgets and automation"
  }')

PERSONA_ID=$(echo $PERSONA_RESPONSE | grep -o '"jobId":[0-9]*' | cut -d':' -f2)
echo "Generated persona with ID: $PERSONA_ID"

# Wait a moment for persona generation to complete (usually < 5 seconds)
sleep 3

# Step 2: Create a feedback session using the generated persona
SESSION_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/feedback-sessions \
  -H "X-User-Id: 1" \
  -H "Content-Type: application/json" \
  -d "{
    \"productIds\": [1],
    \"personaIds\": [$PERSONA_ID]
  }")

SESSION_ID=$(echo $SESSION_RESPONSE | grep -o '"jobId":[0-9]*' | cut -d':' -f2)
echo "Created feedback session with ID: $SESSION_ID"

# Wait for feedback generation (usually < 10 seconds)
sleep 5

# Step 3: Poll for results
curl -X GET http://localhost:8080/api/v1/feedback-sessions/$SESSION_ID \
  -H "X-User-Id: 1" | jq .
```

## Access Service Management Interfaces

### RabbitMQ Management UI

Access the RabbitMQ management console to monitor message queues:

```
URL: http://localhost:15672
Username: guest
Password: guest (from .env)
```

**Useful views:**
- **Queues**: `persona.generation.queue` and `feedback.generation.queue`
- **Connections**: Shows active application connections
- **Messages**: Displays queue depths and message counts

### PostgreSQL Database

Connect directly to the database:

```bash
# Using psql (if installed)
psql -h localhost -U postgres -d personadb -W

# Enter password when prompted (from .env)

# Useful queries:
# List all users
SELECT * FROM users;

# List all personas and their status
SELECT id, name, status, user_id FROM personas;

# List feedback session results
SELECT * FROM feedback_results WHERE status = 'COMPLETED';

# Check message queue depths
SELECT COUNT(*) as queue_depth FROM feedback_results WHERE status = 'PENDING';
```

### Redis Cache

Connect to Redis:

```bash
# Using redis-cli (if installed)
redis-cli -p 6379 -a redispass

# View cached personas
KEYS persona*

# Get a cached persona (example)
GET "personaCache::Your persona generation prompt here"

# Monitor real-time commands
MONITOR
```

## AI Provider & Model Configuration Guide

The system supports multiple AI providers and models. You can switch between them without changing the codebase.

### Supported Providers

| Provider | API Key Format | URL | Notes |
|----------|---|---|---|
| **OpenRouter** | `sk-or-...` | https://openrouter.ai/api/v1/chat/completions | Full model access, pay-as-you-go |
| **AgentRouter** | `sk-or-v1-...` | https://api.agentrouter.ai/v1/chat/completions | Optimized routing, competitive pricing |

### Available Models

Both providers support multiple LLM models. Examples:

**Anthropic:**
- `anthropic/claude-3-5-sonnet` - High performance, best for complex tasks
- `anthropic/claude-3-5-haiku` - Fast, lightweight, lower cost
- `anthropic/claude-3-opus` - Maximum capability

**OpenAI:**
- `openai/gpt-4-turbo` - Advanced reasoning and analysis
- `openai/gpt-4o` - Multimodal capabilities
- `openai/gpt-3.5-turbo` - Fast and cost-effective

**Other providers:**
- `mistral/mistral-large` - Fast open-source model
- `meta-llama/llama-2-70b-chat` - Open source alternative
- And many more available through OpenRouter/AgentRouter

See [OpenRouter Models](https://openrouter.ai/docs#models) or [AgentRouter Models](https://agentrouter.ai/docs#models) for complete list.

### How to Switch Provider & Model

#### Option 1: Using Environment Variables (Recommended)

Edit your `.env` file:

```bash
# Set which provider to use
AI_PROVIDER=agentrouter

# Set which model to use for each provider
OPENROUTER_MODEL=anthropic/claude-3-5-sonnet
AGENTROUTER_MODEL=anthropic/claude-3-5-sonnet

# Provide credentials for the provider you're using
AGENTROUTER_API_KEY=sk-or-v1-your-agentrouter-key-here
OPENROUTER_API_KEY=sk-or-your-openrouter-key-here  # optional if not using OpenRouter
```

Then restart the application.

#### Option 2: Direct Configuration File

Edit `src/main/resources/application.properties`:

```properties
# ======== AI Provider Configuration ========
app.ai.provider=agentrouter

# ======== OpenRouter API Configuration ========
app.openrouter.api-key=sk-or-YOUR_OPENROUTER_API_KEY_HERE
app.openrouter.model=anthropic/claude-3-5-sonnet
app.openrouter.retry-delay-ms=1000

# ======== AgentRouter API Configuration ========
app.agentrouter.api-key=sk-or-v1-YOUR_AGENTROUTER_API_KEY_HERE
app.agentrouter.model=anthropic/claude-3-5-sonnet
app.agentrouter.retry-delay-ms=1000
```

### Comparing Providers

**When to use OpenRouter:**
- Need access to wide variety of models
- Want to compare different LLM providers
- Building provider-agnostic applications

**When to use AgentRouter:**
- Want optimized routing and load balancing
- Need consistent performance
- Prefer competitive pricing

### API Compatibility

Both providers use the same OpenAI-compatible API format, so switching is seamless:

```json
{
  "model": "<YOUR_CONFIGURED_MODEL>",
  "messages": [
    {
      "role": "system",
      "content": "Your system prompt"
    },
    {
      "role": "user",
      "content": "Your user message"
    }
  ]
}
```

For example, if you configure `app.agentrouter.model=openai/gpt-4o`, the API will use GPT-4o. If you set it to `anthropic/claude-3-5-sonnet`, it will use Claude. You can change this anytime in your configuration.

The `AIGatewayService` automatically handles provider-specific details (URLs, authentication, retries, and model selection) based on your configuration.

---

## Testing the Application

### Run Tests

```bash
# Run all tests
mvn test

# Run integration tests only
mvn verify

# Run specific test class
mvn test -Dtest=FeedbackServiceIntegrationTest

# Run with coverage report
mvn clean test jacoco:report
# View report at: target/site/jacoco/index.html
```

### Integration Test Coverage

The application includes comprehensive integration tests:

- ✅ FeedbackService creates proper entity relationships
- ✅ Multiple personas per session
- ✅ Validation for maximum products/personas
- ✅ User ownership verification
- ✅ Cascade delete behaviors
- ✅ Session completion logic

## Architecture Overview

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      REST API Layer                         │
│  POST /api/v1/personas     POST /api/v1/feedback-sessions   │
│  GET /api/v1/feedback-sessions/{id}                        │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                   Service Layer                             │
│  PersonaService          FeedbackService      AIGatewayService
│  - Validation            - Session creation   - Multi-provider support
│  - Task publishing       - Task publishing    - OpenRouter & AgentRouter
│                                                - Token optimization
└──────────────────┬──────────────────┬────────────────────────┘
                   │                  │
        ┌──────────▼──────────┐      └──────┐
        │                     │             │
        │   RabbitMQ Queues   │             │
        │                     │             │
        │ persona.generation  │ feedback    │
        │ .queue              │ .generation │
        │                     │ .queue      │
        └──────────┬──────────┘             │
                   │                       │
        ┌──────────▼──────────┐   ┌────────▼──────────┐
        │  PersonaTaskConsumer│   │FeedbackTaskConsumer
        │                     │   │
        │ AI → Update Persona │   │ AI → Update Result
        └────────┬────────────┘   └────────┬──────────┘
                 │                         │
                 └────────────┬────────────┘
                              │
        ┌─────────────────────▼─────────────────────┐
        │       Spring Data JPA + PostgreSQL        │
        │  Users, Products, Personas, Sessions,    │
        │  FeedbackResults                         │
        └─────────────────────┬─────────────────────┘
                              │
        ┌─────────────────────▼─────────────────────┐
        │  Caching (Redis) + Persistence            │
        │  24-hour persona cache by prompt         │
        └──────────────────────────────────────────┘
```

### Data Flow

1. **Persona Generation**
   - API receives prompt → PersonaService validates → Creates Persona (GENERATING)
   - Task published to `persona.generation.queue`
   - PersonaTaskConsumer processes → Calls AIGatewayService → Selects configured provider (OpenRouter or AgentRouter) → Updates Persona (ACTIVE)
   - AI response cached by prompt for reusability

2. **Feedback Generation**
   - API receives products + personas → FeedbackService validates ownership
   - Creates FeedbackSession (PENDING) + FeedbackResults (PENDING)
   - Task published to `feedback.generation.queue` for each product-persona pair
   - FeedbackTaskConsumer processes → Fetches cached persona + product → Calls AIGatewayService with selected provider
   - Updates FeedbackResult (COMPLETED)
   - When all results done → Updates FeedbackSession (COMPLETED)

## Configuration Files

### Key Configuration Files

| File | Purpose |
|------|---------|
| `application.properties` | Default configuration (localhost) |
| `application-docker.properties` | Docker configuration (uses service names) |
| `.env.example` | Environment variable template |
| `docker-compose.yml` | Infrastructure orchestration |
| `schema.sql` | Database DDL (auto-loaded on startup) |
| `data.sql` | Test data (auto-loaded on startup) |

### Environment Profiles

- **Local Development**: `mvn spring-boot:run` (uses application.properties)
- **Docker**: `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=docker"` (uses application-docker.properties)
- **Testing**: Automatically uses H2 in-memory database with application-test.properties

## Troubleshooting

### Services won't start

**Problem:** `docker-compose up` fails with connection errors

**Solution:**
```bash
# Check service logs
docker-compose logs postgres
docker-compose logs redis
docker-compose logs rabbitmq

# Verify ports are available
# Port 5432 (PostgreSQL), 6379 (Redis), 5672/15672 (RabbitMQ)
netstat -an | grep -E "(5432|6379|5672|15672)"

# If ports are in use, either:
# 1. Stop other services using those ports
# 2. Change ports in docker-compose.yml
```

### Application can't connect to database

**Problem:** `org.postgresql.util.PSQLException: Connection to postgres:5432 refused`

**Solution:**
```bash
# 1. Verify PostgreSQL is running
docker-compose ps | grep postgres

# 2. Check database logs
docker-compose logs postgres

# 3. Verify .env file has correct POSTGRES_PASSWORD
cat .env | grep POSTGRES

# 4. Test connection directly
psql -h localhost -U postgres -d personadb -W
```

### AI Provider API key not working

**Problem:** `AIGatewayException: Failed to call OpenRouter/AgentRouter API`

**Solution:**

First, check which provider is configured:
```bash
# Check which provider is active
grep "app.ai.provider" src/main/resources/application.properties

# Or check .env file
grep "AI_PROVIDER" .env
```

**For OpenRouter:**
```bash
# 1. Verify .env has OPENROUTER_API_KEY set
grep OPENROUTER_API_KEY .env

# 2. Test API key directly
curl https://openrouter.ai/api/v1/models \
  -H "Authorization: Bearer sk-or-YOUR_KEY_HERE"

# 3. Check OpenRouter dashboard
# Visit https://openrouter.ai/account
```

**For AgentRouter:**
```bash
# 1. Verify .env has AGENTROUTER_API_KEY set
grep AGENTROUTER_API_KEY .env

# 2. Test API key directly
curl https://api.agentrouter.ai/v1/models \
  -H "Authorization: Bearer sk-or-v1-YOUR_KEY_HERE"

# 3. Check AgentRouter dashboard
# Visit https://agentrouter.ai/account
```

**General debugging:**
- Ensure API key format is correct (starts with `sk-or-` for OpenRouter, `sk-or-v1-` for AgentRouter)
- Verify key has sufficient credits/quota
- Check that key has not expired or been revoked
- Check application logs for exact error message

### Tests fail

**Problem:** Integration tests fail with database errors

**Solution:**
```bash
# Clear H2 database cache
rm -rf ~/.h2.server

# Run tests with clean Maven
mvn clean test

# Run specific test with debug logging
mvn test -Dtest=FeedbackServiceIntegrationTest -X

# Or in IDE, right-click test class and "Run with Debug"
```

## Development Workflow

### Adding a New Feature

1. Create feature branch
2. Write tests first (TDD)
3. Implement feature
4. Run integration tests: `mvn verify`
5. Commit with Russian message: `git commit -m "Описание изменений"`
6. Push and create pull request

### Debugging in IDE

**IntelliJ IDEA / Eclipse:**
1. Set breakpoint in code
2. Run: `mvn spring-boot:run` with debugging enabled
3. IDE debugger will attach automatically

**Example with Maven:**
```bash
mvn -Dspring-boot.run.arguments="--spring.profiles.active=docker" spring-boot:run -DskipTests
```

## Production Considerations

### Before deploying to production:

1. **Security**
   - ✓ Use strong, unique passwords (20+ characters)
   - ✓ Store secrets in vault/secrets manager, not .env
   - ✓ Enable SSL/TLS for all services
   - ✓ Implement JWT authentication (not X-User-Id mock)
   - ✓ Add request rate limiting
   - ✓ Validate all inputs rigorously

2. **Database**
   - ✓ Enable automated backups
   - ✓ Configure replication for high availability
   - ✓ Set up monitoring and alerting
   - ✓ Regular VACUUM and ANALYZE

3. **Message Queue**
   - ✓ Enable persistence (AOF for Redis, default queues for RabbitMQ)
   - ✓ Set up dead-letter queues for failed messages
   - ✓ Implement message TTL strategies

4. **Monitoring**
   - ✓ Log aggregation (ELK, Splunk, etc.)
   - ✓ Metrics collection (Prometheus, Grafana)
   - ✓ Distributed tracing (Jaeger, DataDog)
   - ✓ APM for performance monitoring

5. **Scaling**
   - ✓ Horizontal scaling with load balancer
   - ✓ Database connection pooling tuning
   - ✓ Cache warming strategies
   - ✓ Async worker scaling

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m "описание изменений"`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues, questions, or suggestions:
- Open an issue on GitHub
- Check existing documentation in `src/main/java/ru/tigran/personafeedbackengine/*/CLAUDE.md`
- Review code comments and architecture docs

## Changelog

### Version 0.0.2 (Multi-Provider Support)
- ✨ Added multi-provider AI support (OpenRouter & AgentRouter)
- ✨ Provider selection via configuration (no code changes needed)
- ✨ Automatic provider detection and routing
- 📝 Updated documentation with provider configuration guide
- 🔄 Seamless switching between providers without downtime

### Version 0.0.1-SNAPSHOT (MVP)
- ✨ Initial MVP with persona generation and feedback simulation
- ✨ RabbitMQ-based async processing
- ✨ Redis caching for persona reusability
- ✨ REST API with async job tracking
- ✨ Comprehensive integration tests
- ✨ Docker support with docker-compose
