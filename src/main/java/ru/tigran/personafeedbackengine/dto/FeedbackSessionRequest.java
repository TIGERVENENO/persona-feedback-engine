package ru.tigran.personafeedbackengine.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for creating a new feedback session.
 *
 * Generates feedback from multiple personas on multiple products.
 * Constraints:
 * - productIds: 1-5 products per session
 * - personaIds: 1-5 personas per session
 * - All IDs must be positive integers (> 0)
 */
public record FeedbackSessionRequest(
        @NotEmpty(message = "Product IDs cannot be empty")
        @Size(min = 1, max = 5, message = "Must have 1-5 products per session")
        List<@Positive(message = "Product IDs must be positive") Long> productIds,

        @NotEmpty(message = "Persona IDs cannot be empty")
        @Size(min = 1, max = 5, message = "Must have 1-5 personas per session")
        List<@Positive(message = "Persona IDs must be positive") Long> personaIds
) {
}
