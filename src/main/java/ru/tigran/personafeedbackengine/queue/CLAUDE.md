# queue/

## Purpose
Message consumers for asynchronous task processing from RabbitMQ queues.
Consumers delegate to dedicated generation services following Single Responsibility Principle.

## Key Classes

### PersonaTaskConsumer (Orchestrator)
- Listens to `persona.generation.queue`
- Consumes `PersonaGenerationTask` messages
- **Responsibility**: Message routing and error handling only
- **Delegates business logic to**: `PersonaGenerationService.generatePersona(task)`
- Error handling: Catches exceptions and marks Persona as FAILED

### PersonaGenerationService
- **Responsibility**: Core persona generation business logic
- Workflow:
  1. Fetch Persona entity from database
  2. Check idempotency: if already ACTIVE, skip; if FAILED, retry; if unexpected status, skip
  3. Call `AIGatewayService.generatePersonaDetails(prompt)`
  4. Parse JSON response and validate required fields (nm, dd, g, ag, r)
  5. Update Persona entity with generated details
  6. Change state to ACTIVE
  7. Persist changes via repository

### FeedbackTaskConsumer (Orchestrator)
- Listens to `feedback.generation.queue`
- Consumes `FeedbackGenerationTask` messages
- **Responsibility**: Message routing and error handling only
- **Delegates business logic to**: `FeedbackGenerationService.generateFeedback(task)`
- Error handling: Catches exceptions and marks FeedbackResult as FAILED

### FeedbackGenerationService
- **Responsibility**: Core feedback generation business logic and session completion
- Workflow:
  1. Fetch FeedbackResult, Persona, Product from database
  2. Check idempotency: if already COMPLETED, skip; if FAILED, retry
  3. Mark result as IN_PROGRESS
  4. Call `AIGatewayService.generateFeedbackForProduct(...)`
  5. Update FeedbackResult with generated feedback text
  6. Change FeedbackResult state to COMPLETED
  7. Check if all FeedbackResults for the session are done via `checkAndUpdateSessionCompletion(sessionId)`
  8. If complete, update FeedbackSession to COMPLETED using distributed lock

## Concurrency & Synchronization
- **FeedbackGenerationService** uses Redisson distributed locks for session status updates
- Lock key pattern: `feedback-session-lock:{sessionId}`
- Lock timeout: 10 seconds
- Prevents race conditions when multiple consumer instances update the same session status
- Enables safe horizontal scaling

## Error Handling
- Consumers catch exceptions and transition entities to FAILED state
- Services throw exceptions for caller to handle
- Idempotency ensures safe message redelivery
- No retry queue or exponential backoff (MVP scope)
- Distributed locks ensure atomic status updates across multiple instances

## Design Pattern
This follows the **Single Responsibility Principle**:
- **Consumers**: Handle message routing and orchestration only
- **Services**: Handle business logic, domain operations, and persistence
