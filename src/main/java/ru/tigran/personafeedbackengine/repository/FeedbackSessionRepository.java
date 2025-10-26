package ru.tigran.personafeedbackengine.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.tigran.personafeedbackengine.model.FeedbackSession;

import java.util.Optional;

@Repository
public interface FeedbackSessionRepository extends JpaRepository<FeedbackSession, Long> {
    Optional<FeedbackSession> findByUserIdAndId(Long userId, Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fs FROM FeedbackSession fs WHERE fs.id = :sessionId")
    Optional<FeedbackSession> findByIdForUpdate(@Param("sessionId") Long sessionId);

    @Modifying
    @Query("UPDATE FeedbackSession fs SET fs.status = :status " +
           "WHERE fs.id = :sessionId AND fs.status != :status")
    int updateStatusIfNotAlready(@Param("sessionId") Long sessionId,
                                 @Param("status") FeedbackSession.FeedbackSessionStatus status);
}
