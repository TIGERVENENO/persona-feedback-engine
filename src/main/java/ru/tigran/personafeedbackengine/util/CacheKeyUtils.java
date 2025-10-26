package ru.tigran.personafeedbackengine.util;

/**
 * Utility class for cache key normalization.
 * Ensures consistent cache keys regardless of minor prompt variations.
 */
public class CacheKeyUtils {

    private CacheKeyUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Normalizes a prompt string for consistent cache key generation.
     * Applies the following transformations:
     * 1. Trim leading/trailing whitespace
     * 2. Convert to lowercase
     * 3. Replace multiple spaces with single space
     * 4. Remove extra newlines and tabs
     *
     * Example:
     * Input:  "  A developer  from  Berlin  \n  who loves   AI  "
     * Output: "a developer from berlin who loves ai"
     *
     * @param prompt The raw prompt string
     * @return Normalized prompt for cache key
     */
    public static String normalizePrompt(String prompt) {
        if (prompt == null) {
            return "";
        }
        return prompt
                .trim()                          // Remove leading/trailing whitespace
                .toLowerCase()                   // Convert to lowercase for consistency
                .replaceAll("\\s+", " ")         // Replace multiple spaces with single space
                .replaceAll("[\\n\\r\\t]+", " "); // Replace newlines and tabs with single space
    }

    /**
     * Generates a cache key combining userId and normalized prompt.
     *
     * @param userId User ID
     * @param prompt Raw prompt string
     * @return Cache key in format "userId:normalizedPrompt"
     */
    public static String generatePersonaCacheKey(Long userId, String prompt) {
        return userId + ":" + normalizePrompt(prompt);
    }
}
