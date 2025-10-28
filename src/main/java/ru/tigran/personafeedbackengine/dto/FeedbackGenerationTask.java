package ru.tigran.personafeedbackengine.dto;

import java.io.Serializable;

public record FeedbackGenerationTask(
        Long resultId,
        Long productId,
        Long personaId,
        String language
) implements Serializable {
}
