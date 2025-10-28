package ru.tigran.personafeedbackengine.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FeedbackSessionResponse(
        Long id,
        String status,
        String language,
        LocalDateTime createdAt,
        List<FeedbackResultDTO> feedbackResults,
        AggregatedInsights aggregatedInsights,  // Агрегированные insights (null если сессия не завершена)
        Integer pageNumber,
        Integer pageSize,
        Long totalCount
) {
    /**
     * Constructor for non-paginated responses (all results)
     */
    public FeedbackSessionResponse(
            Long id,
            String status,
            String language,
            LocalDateTime createdAt,
            List<FeedbackResultDTO> feedbackResults,
            AggregatedInsights aggregatedInsights
    ) {
        this(id, status, language, createdAt, feedbackResults, aggregatedInsights, null, null, null);
    }
}
