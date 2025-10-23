# queue/

## Purpose
Message consumers for asynchronous task processing from RabbitMQ queues.

## Key Classes

### PersonaTaskConsumer
- Listens to `persona.generation.queue`
- Consumes `PersonaGenerationTask` messages
- Workflow:
  1. Fetch Persona entity from database
  2. Call `AIGatewayService.generatePersonaDetails(prompt)`
  3. Update Persona entity with generated details
  4. Change state to ACTIVE
  5. Log errors and transition to FAILED on exception

### FeedbackTaskConsumer
- Listens to `feedback.generation.queue`
- Consumes `FeedbackGenerationTask` messages
- Workflow:
  1. Fetch Persona, Product, FeedbackResult from database
  2. Call `AIGatewayService.generateFeedbackForProduct(...)`
  3. Update FeedbackResult with generated feedback text
  4. Change FeedbackResult state to COMPLETED
  5. Check if all FeedbackResults for the session are done; if yes, update FeedbackSession to COMPLETED
  6. Log errors and transition to FAILED on exception

## Error Handling
- Exceptions caught and logged
- Entities transitioned to FAILED state
- No retry queue or exponential backoff (MVP scope)
