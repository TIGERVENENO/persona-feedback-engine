package ru.tigran.personafeedbackengine.dto;

import java.io.Serializable;

public record PersonaGenerationTask(
        Long personaId,
        String userPrompt
) implements Serializable {
}
