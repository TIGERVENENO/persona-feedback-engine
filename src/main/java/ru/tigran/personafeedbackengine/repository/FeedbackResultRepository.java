package ru.tigran.personafeedbackengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.tigran.personafeedbackengine.model.FeedbackResult;

import java.util.List;

@Repository
public interface FeedbackResultRepository extends JpaRepository<FeedbackResult, Long> {
    List<FeedbackResult> findByFeedbackSessionId(Long feedbackSessionId);

    @Query("SELECT COUNT(fr) FROM FeedbackResult fr " +
           "WHERE fr.feedbackSession.id = :sessionId AND fr.status = :status")
    long countBySessionAndStatus(@Param("sessionId") Long feedbackSessionId,
                                 @Param("status") FeedbackResult.FeedbackResultStatus status);

    @Query("SELECT fr FROM FeedbackResult fr " +
           "LEFT JOIN FETCH fr.persona " +
           "LEFT JOIN FETCH fr.product " +
           "WHERE fr.feedbackSession.id = :sessionId")
    List<FeedbackResult> findByFeedbackSessionIdWithDetails(@Param("sessionId") Long sessionId);
}
