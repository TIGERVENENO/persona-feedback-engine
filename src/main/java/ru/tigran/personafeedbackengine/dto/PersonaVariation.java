package ru.tigran.personafeedbackengine.dto;

/**
 * Represents a specific variation of persona demographics.
 * Used internally for generating multiple personas from TargetAudience.
 *
 * Example:
 * {
 *   "gender": "male",
 *   "age": 27,
 *   "region": "moscow",
 *   "incomeLevel": "medium"
 * }
 */
public record PersonaVariation(
        String gender,      // "male", "female", "other"
        Integer age,        // Exact age (e.g., 27, 34)
        String region,      // "moscow", "spb", "regions"
        String incomeLevel  // "low", "medium", "high"
) {
}
