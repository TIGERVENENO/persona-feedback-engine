# dto/

## Purpose
Data transfer objects for API layer and message queue communication.

## API Request DTOs

### PersonaGenerationRequest
- `prompt: String` - User-provided prompt for persona generation (max 2000 chars)

### FeedbackSessionRequest
- `productIds: List<Long>` - Products to get feedback on (max 5)
- `personaIds: List<Long>` - Personas to generate feedback from (max 5)

## API Response DTOs

### JobResponse
- `jobId: Long` - ID of created job (Persona or FeedbackSession)
- `status: String` - Initial status (e.g., "GENERATING", "PENDING")
- Generic response for all async operations

### PersonaResponse
- `id: Long`
- `name: String`
- `detailedDescription: String`
- `gender: String`
- `ageGroup: String`
- `race: String`
- `avatarUrl: String`
- `status: String`

### ProductResponse
- `id: Long`
- `name: String`
- `description: String`

### FeedbackSessionResponse
- `id: Long`
- `status: String`
- `createdAt: LocalDateTime`
- `feedbackResults: List<FeedbackResultDTO>`

### FeedbackResultDTO
- `id: Long`
- `feedbackText: String`
- `status: String`
- `personaId: Long`
- `productId: Long`

## Queue Message DTOs

### PersonaGenerationTask
- `personaId: Long` - ID of Persona entity
- `userPrompt: String` - Original prompt for generation

### FeedbackGenerationTask
- `resultId: Long` - ID of FeedbackResult to update
- `productId: Long` - Product being reviewed
- `personaId: Long` - Persona providing feedback
