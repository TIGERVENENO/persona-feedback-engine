package ru.tigran.personafeedbackengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.tigran.personafeedbackengine.model.FeedbackResult;

import java.util.List;

@Repository
public interface FeedbackResultRepository extends JpaRepository<FeedbackResult, Long> {
    List<FeedbackResult> findByFeedbackSessionId(Long feedbackSessionId);
    long countByFeedbackSessionIdAndStatus(Long feedbackSessionId, String status);
}
