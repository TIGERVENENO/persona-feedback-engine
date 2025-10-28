package ru.tigran.personafeedbackengine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import ru.tigran.personafeedbackengine.validation.ISO6391LanguageCode;

import java.util.List;

/**
 * Request DTO for creating a new feedback session.
 *
 * Supports two modes:
 * 1. Explicit personas: Specify personaIds (old approach, still supported)
 * 2. Auto-generate personas: Specify targetAudience + personaCount (new approach)
 *
 * Constraints:
 * - productIds: 1-5 products per session (required)
 * - personaIds: 1-5 personas (optional, mutually exclusive with targetAudience)
 * - targetAudience: Demographics for auto-generation (optional, mutually exclusive with personaIds)
 * - personaCount: 3-7 personas to generate (default: 5, only used with targetAudience)
 * - language: ISO 639-1 two-letter code (EN, RU, FR, etc.)
 *
 * Either personaIds OR targetAudience must be provided, not both.
 */
@Data
public class FeedbackSessionRequest {
    @NotEmpty(message = "Product IDs cannot be empty")
    @Size(min = 1, max = 5, message = "Must have 1-5 products per session")
    private List<@Positive(message = "Product IDs must be positive") Long> productIds;

    // Старый способ (опциональный): явно указанные персоны
    @Size(min = 1, max = 5, message = "Must have 1-5 personas per session")
    private List<@Positive(message = "Persona IDs must be positive") Long> personaIds;

    // Новый способ (опциональный): автогенерация персон из целевой аудитории
    @Valid
    private TargetAudience targetAudience;

    // Количество персон для генерации (используется только с targetAudience)
    @Min(value = 3, message = "Persona count must be at least 3")
    @Max(value = 7, message = "Persona count must be at most 7")
    private Integer personaCount = 5;  // Default value

    @NotNull(message = "Language code cannot be null")
    @ISO6391LanguageCode
    private String language;

    /**
     * Validates that exactly one mode is specified.
     * Either personaIds OR targetAudience must be provided, not both or neither.
     */
    @AssertTrue(message = "Either personaIds or targetAudience must be provided, but not both")
    private boolean isValidMode() {
        boolean hasPersonaIds = personaIds != null && !personaIds.isEmpty();
        boolean hasTargetAudience = targetAudience != null;

        // Exactly one must be true (XOR)
        return hasPersonaIds ^ hasTargetAudience;
    }
}
