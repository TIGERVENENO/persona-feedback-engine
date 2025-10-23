package ru.tigran.personafeedbackengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.tigran.personafeedbackengine.model.FeedbackSession;

import java.util.Optional;

@Repository
public interface FeedbackSessionRepository extends JpaRepository<FeedbackSession, Long> {
    Optional<FeedbackSession> findByUserIdAndId(Long userId, Long sessionId);
}
