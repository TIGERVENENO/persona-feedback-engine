package ru.tigran.personafeedbackengine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for generating a new AI persona using structured input.
 *
 * Triggers async persona generation workflow via RabbitMQ.
 *
 * Structure:
 * - demographics: Age, gender, location, occupation, income
 * - psychographics: Values, lifestyle, pain points
 *
 * Notes:
 * - Structured data used as cache key (JSON representation)
 * - Generation is async, client must poll the persona endpoint to check status
 * - Persona bio always generated in English for consistency
 */
public record PersonaGenerationRequest(
        @NotNull(message = "Demographics cannot be null")
        @Valid
        PersonaDemographics demographics,

        @NotNull(message = "Psychographics cannot be null")
        @Valid
        PersonaPsychographics psychographics
) {
}
