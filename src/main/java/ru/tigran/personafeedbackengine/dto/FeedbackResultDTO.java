package ru.tigran.personafeedbackengine.dto;

public record FeedbackResultDTO(
        Long id,
        String feedbackText,
        String status,
        Long personaId,
        Long productId
) {
}
