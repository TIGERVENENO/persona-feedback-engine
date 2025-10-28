package ru.tigran.personafeedbackengine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "feedback_sessions", indexes = {
    @Index(name = "idx_feedback_session_user", columnList = "user_id"),
    @Index(name = "idx_feedback_session_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"user", "feedbackResults"})
public class FeedbackSession extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeedbackSessionStatus status;

    @Column(nullable = false, length = 2)
    private String language;

    // AI-aggregated insights: {averageScore, purchaseIntentPercent, keyThemes: [{theme, mentions}]}
    @Column(columnDefinition = "JSONB")
    private String aggregatedInsights;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "feedbackSession", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FeedbackResult> feedbackResults;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = FeedbackSessionStatus.PENDING;
        }
    }

    public enum FeedbackSessionStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED
    }
}
