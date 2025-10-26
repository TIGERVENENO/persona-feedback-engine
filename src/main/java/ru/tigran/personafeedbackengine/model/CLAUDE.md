# model/

## Purpose
JPA entities and domain models for the persona feedback engine.

## Key Entities

### User
- Represents a platform user/marketer
- Root entity for data isolation via user_id in all queries
- Authentication: Email-based (no username field)
- Fields:
  - `id: Long` - Primary key
  - `email: String` - Unique user email (used for authentication instead of username)
  - `passwordHash: String` - BCrypt hashed password (60 chars, work factor 10)
  - `isActive: Boolean` - Account active status (default: true)
  - `deleted: Boolean` - Soft delete flag (default: false)
  - `createdAt: LocalDateTime` - Audit timestamp (auto)
  - `updatedAt: LocalDateTime` - Audit timestamp (auto)
- Cascades PERSIST and MERGE to owned Personas, Products, FeedbackSessions (NOT CASCADE on delete)

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
