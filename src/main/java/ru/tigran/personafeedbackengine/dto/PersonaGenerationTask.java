package ru.tigran.personafeedbackengine.dto;

import java.io.Serializable;

/**
 * Queue message for persona generation task.
 *
 * Contains:
 * - personaId: ID of Persona entity to update
 * - demographicsJson: JSON string with demographics (age, gender, location, occupation, income)
 * - psychographicsJson: JSON string with psychographics (values, lifestyle, painPoints)
 */
public record PersonaGenerationTask(
        Long personaId,
        String demographicsJson,
        String psychographicsJson
) implements Serializable {
}
