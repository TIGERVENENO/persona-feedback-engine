package ru.tigran.personafeedbackengine.dto;

import java.util.List;

public record FeedbackResultDTO(
        Long id,
        String feedbackText,
        Integer purchaseIntent,
        List<String> keyConcerns,
        String status,
        Long personaId,
        Long productId
) {
}
