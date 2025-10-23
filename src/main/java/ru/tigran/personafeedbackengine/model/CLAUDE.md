# model/

## Purpose
JPA entities and domain models for the persona feedback engine.

## Key Entities

### User
- Represents a platform user/marketer
- Root entity for data isolation
- Cascades ALL operations to owned Personas and Products

### Product
- A product/service to receive feedback on
- Owned by a User
- Referenced by FeedbackResults

### Persona
- AI-generated character profiles for realistic feedback simulation
- Owned by a User
- States: GENERATING → ACTIVE or FAILED
- Caching key based on generation prompt (allows reusability across sessions)

### FeedbackSession
- Encapsulates a batch feedback generation request
- Owned by a User
- States: PENDING → IN_PROGRESS → COMPLETED or FAILED
- Contains multiple FeedbackResult entities

### FeedbackResult
- Individual feedback entry from one persona on one product
- References FeedbackSession, Persona, Product
- States: PENDING → IN_PROGRESS → COMPLETED or FAILED
- Stores generated feedback text

## Relationships
- User 1:N Product
- User 1:N Persona
- User 1:N FeedbackSession
- FeedbackSession 1:N FeedbackResult
- Persona N:1 FeedbackResult
- Product N:1 FeedbackResult
