package ru.tigran.personafeedbackengine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PersonaGenerationRequest(
        @NotBlank(message = "Prompt cannot be blank")
        @Size(min = 1, max = 2000, message = "Prompt must be between 1 and 2000 characters")
        String prompt
) {
}
