# util/

## Purpose
Utility classes for common operations across the application.
Provides helper methods that don't fit into specific domain layers.

## Key Classes

### CacheKeyUtils
Static utility class for cache key generation and normalization.

**STATUS: DEPRECATED - No longer used**
- Persona generation intentionally disabled caching to ensure diverse persona generation
- When generating batch personas (6 personas from same demographic profile), each must be unique
- Each call to AIGatewayService.generatePersonaDetails() now produces a different persona
- Cache key normalization is no longer needed

**Original Purpose (deprecated):**
Was used to ensure consistent cache keys for personas regardless of minor prompt variations (whitespace, case, formatting).

**Methods (deprecated):**

- `normalizePrompt(String prompt) → String` - DEPRECATED
  - Previously normalized prompt string for consistent cache key generation

- `generatePersonaCacheKey(Long userId, String prompt) → String` - DEPRECATED
  - Previously generated cache key combining userId and normalized prompt

**Why it was removed:**
- AIGatewayService no longer uses @Cacheable annotation on generatePersonaDetails()
- Each persona call must produce a unique result for batch generation to work properly
- User receives diverse personas instead of identical ones when generating 6 personas from same parameters

**Design Notes:**
- Kept for reference/history
- Can be removed in future cleanup if not referenced elsewhere
