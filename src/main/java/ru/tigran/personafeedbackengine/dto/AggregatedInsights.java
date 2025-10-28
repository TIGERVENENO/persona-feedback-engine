package ru.tigran.personafeedbackengine.dto;

import java.util.List;

/**
 * AI-aggregated insights from multiple feedback results.
 *
 * Structure:
 * - averageScore: Average purchase intent (1-10)
 * - purchaseIntentPercent: Percentage of personas willing to buy (purchaseIntent >= 7)
 * - keyThemes: Main concerns/themes extracted from all feedback
 *
 * Example:
 * {
 *   "averageScore": 7.2,
 *   "purchaseIntentPercent": 71,
 *   "keyThemes": [
 *     {"theme": "Price too high", "mentions": 4},
 *     {"theme": "Great design", "mentions": 6}
 *   ]
 * }
 */
public record AggregatedInsights(
        Double averageScore,           // Average purchase intent (1-10)
        Integer purchaseIntentPercent, // % of personas with purchaseIntent >= 7
        List<KeyTheme> keyThemes       // Main concerns/themes
) {
    /**
     * Individual theme with mention count.
     */
    public record KeyTheme(
            String theme,    // Theme description (e.g., "Price too high")
            Integer mentions // How many personas mentioned this
    ) {
    }
}
