package ru.tigran.personafeedbackengine.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FeedbackSessionRequest(
        @NotEmpty(message = "Product IDs cannot be empty")
        @Size(max = 5, message = "Maximum 5 products per session")
        List<Long> productIds,

        @NotEmpty(message = "Persona IDs cannot be empty")
        @Size(max = 5, message = "Maximum 5 personas per session")
        List<Long> personaIds
) {
}
