# repository/

## Purpose
Spring Data JPA repositories for database access.

## Key Repositories

### UserRepository
- Extends `JpaRepository<User, Long>`
- Custom methods:
  - `findByEmail(String email)` - Find user by email address (returns Optional<User>)
  - `existsByEmail(String email)` - Check if email already registered (returns boolean)

### ProductRepository
- Extends `JpaRepository<Product, Long>`
- Custom methods:
  - `findByUserIdAndId(Long userId, Long productId)` - Ownership check
  - `findByUserIdAndIdIn(Long userId, List<Long> ids)` - Batch fetch with ownership

### PersonaRepository
- Extends `JpaRepository<Persona, Long>`
- Custom methods:
  - `findByUserIdAndId(Long userId, Long personaId)` - Ownership check
  - `findByUserIdAndIdIn(Long userId, List<Long> ids)` - Batch fetch with ownership
  - May add caching-related queries if needed

### FeedbackSessionRepository
- Extends `JpaRepository<FeedbackSession, Long>`
- Custom methods:
  - `findByUserIdAndId(Long userId, Long sessionId)` - Ownership check
  - `findByIdForUpdate(Long sessionId)` - Fetch with PESSIMISTIC_WRITE lock (prevents race conditions)
  - `updateStatusIfNotAlready(Long sessionId, FeedbackSessionStatus status)` - Atomic UPDATE (only if status changed)

### FeedbackResultRepository
- Extends `JpaRepository<FeedbackResult, Long>`
- Custom methods:
  - `findByFeedbackSessionId(Long sessionId)` - Fetch all results for a session
  - `findByFeedbackSessionIdWithDetails(Long sessionId)` - Fetch results with eager loading of Persona and Product (LEFT JOIN FETCH)
  - `countBySessionAndStatus(Long sessionId, FeedbackResultStatus status)` - Count results by status (safe, uses enum instead of String)

## Design Notes
- All queries include userId filter for data isolation
- No cascade delete operations in repositories (handled at service layer)
- Standard pagination support for list operations
