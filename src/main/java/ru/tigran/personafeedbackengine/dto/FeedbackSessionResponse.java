package ru.tigran.personafeedbackengine.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FeedbackSessionResponse(
        Long id,
        String status,
        String language,
        LocalDateTime createdAt,
        List<FeedbackResultDTO> feedbackResults,
        Integer pageNumber,
        Integer pageSize,
        Long totalCount
) {
    /**
     * Constructor for non-paginated responses (all results)
     */
    public FeedbackSessionResponse(Long id, String status, String language, LocalDateTime createdAt, List<FeedbackResultDTO> feedbackResults) {
        this(id, status, language, createdAt, feedbackResults, null, null, null);
    }
}
