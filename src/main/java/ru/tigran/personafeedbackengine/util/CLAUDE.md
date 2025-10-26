# util/

## Purpose
Utility classes for common operations across the application.
Provides helper methods that don't fit into specific domain layers.

## Key Classes

### CacheKeyUtils
Static utility class for cache key generation and normalization.

**Purpose:**
Ensures consistent cache keys for personas regardless of minor prompt variations (whitespace, case, formatting).

**Methods:**

- `normalizePrompt(String prompt) → String`
  - Normalizes prompt string for consistent cache key generation
  - Transformations applied:
    1. Trim leading/trailing whitespace
    2. Convert to lowercase
    3. Replace multiple spaces with single space
    4. Replace newlines and tabs with single space
  - **Example:**
    ```
    Input:  "  A developer  from  Berlin  \n  who loves   AI  "
    Output: "a developer from berlin who loves ai"
    ```
  - **Usage:** Called before generating persona cache keys
  - Returns empty string if input is null

- `generatePersonaCacheKey(Long userId, String prompt) → String`
  - Generates cache key combining userId and normalized prompt
  - Format: `"userId:normalizedPrompt"`
  - **Example:**
    ```java
    generatePersonaCacheKey(123L, "  Tech enthusiast  ")
    // Returns: "123:tech enthusiast"
    ```
  - **Usage:** Called by AIGatewayService.generatePersonaDetails() for @Cacheable annotation

**Design Notes:**
- Private constructor prevents instantiation (utility class pattern)
- All methods are static and stateless
- Normalization ensures cache hits for semantically identical prompts

## Integration Points

### AIGatewayService
- Uses `generatePersonaCacheKey()` for persona caching
- Cache key format enables user-scoped persona reusability
- Prevents cache collisions between different users with same prompts

## Common Patterns

**Caching persona by prompt:**
```java
@Cacheable(value = "personaCache", key = "T(ru.tigran.personafeedbackengine.util.CacheKeyUtils).generatePersonaCacheKey(#userId, #userPrompt)")
public String generatePersonaDetails(Long userId, String userPrompt) {
    // AI generation logic
}
```

**Why normalization matters:**
- Without normalization: "Tech enthusiast" and "tech  enthusiast  " would be different cache keys
- With normalization: Both map to "tech enthusiast" → cache hit
