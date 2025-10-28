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
- Fields:
  - `id: Long` - Primary key
  - `name: String` - Product name (required, 1-200 chars)
  - `description: String` - Product description (optional, max 5000 chars)
  - `price: BigDecimal` - Product price (optional)
  - `category: String` - Product category (optional, max 100 chars)
  - `keyFeatures: List<String>` - Key product features stored as JSONB (optional)
  - `deleted: Boolean` - Soft delete flag (default: false)
  - `user: User` - Owner of the product (required)
- **JSONB Handling**: keyFeatures stored as JSONB in PostgreSQL
  - Uses custom Hibernate UserType (JsonbStringType) for proper JSONB type handling
  - Uses AttributeConverter (JsonbConverter) for List<String> ↔ JSON conversion

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
- Fields:
  - `id: Long` - Primary key
  - `status: FeedbackSessionStatus` - Current status (PENDING, IN_PROGRESS, COMPLETED, FAILED)
  - `language: String` - ISO 639-1 language code for feedback generation (EN, RU, FR, etc.)
  - `user: User` - Owner of the session
  - `feedbackResults: List<FeedbackResult>` - Collection of feedback results
  - `createdAt: LocalDateTime` - Audit timestamp (auto)
  - `updatedAt: LocalDateTime` - Audit timestamp (auto)

### FeedbackResult
- Individual feedback entry from one persona on one product
- References FeedbackSession, Persona, Product
- States: PENDING → IN_PROGRESS → COMPLETED or FAILED
- Stores generated feedback text

## Utility Classes

### JsonbStringType
- Custom Hibernate 6 UserType for PostgreSQL JSONB type handling
- **Problem it solves**: PostgreSQL JSONB type casting error (VARCHAR vs JSONB type mismatch)
- **How it works**:
  - Tells Hibernate to use `Types.OTHER` for JSONB columns
  - Properly marshalls Java String ↔ PostgreSQL JSONB in prepared statements
  - Handles null values gracefully
- Used by: Product.keyFeatures field

### JsonbConverter
- JPA AttributeConverter for List<String> ↔ JSON string conversion
- **Functionality**:
  - `convertToDatabaseColumn()`: List<String> → JSON string
  - `convertToEntityAttribute()`: JSON string → List<String>
  - Uses Jackson ObjectMapper for serialization
- Works in tandem with JsonbStringType for complete JSONB support

## Relationships
- User 1:N Product
- User 1:N Persona
- User 1:N FeedbackSession
- FeedbackSession 1:N FeedbackResult
- Persona N:1 FeedbackResult
- Product N:1 FeedbackResult
