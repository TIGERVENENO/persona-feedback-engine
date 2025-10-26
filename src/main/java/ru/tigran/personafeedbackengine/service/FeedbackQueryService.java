package ru.tigran.personafeedbackengine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tigran.personafeedbackengine.dto.FeedbackResultDTO;
import ru.tigran.personafeedbackengine.dto.FeedbackSessionResponse;
import ru.tigran.personafeedbackengine.exception.UnauthorizedException;
import ru.tigran.personafeedbackengine.model.FeedbackSession;
import ru.tigran.personafeedbackengine.model.FeedbackResult;
import ru.tigran.personafeedbackengine.repository.FeedbackResultRepository;
import ru.tigran.personafeedbackengine.repository.FeedbackSessionRepository;

import java.util.List;

@Slf4j
@Service
public class FeedbackQueryService {

    private final FeedbackSessionRepository feedbackSessionRepository;
    private final FeedbackResultRepository feedbackResultRepository;

    public FeedbackQueryService(
            FeedbackSessionRepository feedbackSessionRepository,
            FeedbackResultRepository feedbackResultRepository
    ) {
        this.feedbackSessionRepository = feedbackSessionRepository;
        this.feedbackResultRepository = feedbackResultRepository;
    }

    /**
     * Retrieves feedback session with caching.
     * Cache is invalidated when session status changes.
     *
     * @param userId User ID for ownership validation
     * @param sessionId Session ID
     * @return Cached FeedbackSessionResponse
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "feedbackSessions", key = "#userId + ':' + #sessionId")
    public FeedbackSessionResponse getFeedbackSessionCached(Long userId, Long sessionId) {
        log.debug("Fetching feedback session {} for user {}", sessionId, userId);

        FeedbackSession session = feedbackSessionRepository.findByUserIdAndId(userId, sessionId)
                .orElseThrow(() -> new UnauthorizedException(
                        "Feedback session not found or does not belong to this user",
                        "UNAUTHORIZED_ACCESS"
                ));

        List<FeedbackResult> results = feedbackResultRepository.findByFeedbackSessionIdWithDetails(sessionId);

        List<FeedbackResultDTO> resultDTOs = results.stream()
                .map(result -> new FeedbackResultDTO(
                        result.getId(),
                        result.getFeedbackText(),
                        result.getStatus().name(),
                        result.getPersona().getId(),
                        result.getProduct().getId()
                ))
                .toList();

        return new FeedbackSessionResponse(
                session.getId(),
                session.getStatus().name(),
                session.getCreatedAt(),
                resultDTOs
        );
    }

    /**
     * Invalidates feedback session cache after status update.
     * Called from FeedbackTaskConsumer when session completes.
     *
     * @param userId User ID
     * @param sessionId Session ID
     */
    @CacheEvict(value = "feedbackSessions", key = "#userId + ':' + #sessionId")
    public void invalidateSessionCache(Long userId, Long sessionId) {
        log.debug("Invalidated cache for feedback session {} user {}", sessionId, userId);
    }
}
