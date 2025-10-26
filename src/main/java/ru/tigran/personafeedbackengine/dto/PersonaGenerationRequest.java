package ru.tigran.personafeedbackengine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for generating a new AI persona.
 *
 * Triggers async persona generation workflow via RabbitMQ.
 *
 * Constraints:
 * - prompt: Required, non-blank string, 1-2000 characters
 *
 * Notes:
 * - Prompt is used as cache key, so identical prompts will reuse cached personas
 * - Generation is async, client must poll the persona endpoint to check status
 */
public record PersonaGenerationRequest(
        @NotBlank(message = "Prompt cannot be blank")
        @Size(min = 1, max = 2000, message = "Prompt must be between 1 and 2000 characters")
        String prompt
) {
}
