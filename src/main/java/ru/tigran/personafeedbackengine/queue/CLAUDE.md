# queue/

## Purpose
Message consumers for asynchronous task processing from RabbitMQ queues.

## Key Classes

### PersonaTaskConsumer
- Listens to `persona.generation.queue`
- Consumes `PersonaGenerationTask` messages
- **Idempotent**: Skips processing if Persona is already ACTIVE
- Workflow:
  1. Fetch Persona entity from database
  2. Check idempotency: if already ACTIVE, skip; if FAILED, retry; if unexpected status, skip
  3. Call `AIGatewayService.generatePersonaDetails(prompt)`
  4. Validate response JSON fields
  5. Update Persona entity with generated details
  6. Change state to ACTIVE
  7. Log errors and transition to FAILED on exception

### FeedbackTaskConsumer
- Listens to `feedback.generation.queue`
- Consumes `FeedbackGenerationTask` messages
- **Idempotent**: Skips processing if FeedbackResult is already COMPLETED
- Workflow:
  1. Fetch FeedbackResult, Persona, Product from database
  2. Check idempotency: if already COMPLETED, skip; if FAILED, retry
  3. Mark result as IN_PROGRESS
  4. Call `AIGatewayService.generateFeedbackForProduct(...)`
  5. Update FeedbackResult with generated feedback text
  6. Change FeedbackResult state to COMPLETED
  7. Check if all FeedbackResults for the session are done; if yes, update FeedbackSession to COMPLETED
  8. Log errors and transition to FAILED on exception

## Concurrency & Synchronization
- **FeedbackTaskConsumer** uses Redisson distributed locks for session status updates
- Lock key pattern: `feedback-session-lock:{sessionId}`
- Lock timeout: 10 seconds
- Prevents race conditions when multiple consumer instances update the same session status
- Enables safe horizontal scaling

## Error Handling
- Exceptions caught and logged
- Entities transitioned to FAILED state
- Idempotency ensures safe message redelivery
- No retry queue or exponential backoff (MVP scope)
- Distributed locks ensure atomic status updates across multiple instances
