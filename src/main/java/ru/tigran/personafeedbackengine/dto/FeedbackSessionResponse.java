package ru.tigran.personafeedbackengine.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FeedbackSessionResponse(
        Long id,
        String status,
        LocalDateTime createdAt,
        List<FeedbackResultDTO> feedbackResults
) {
}
