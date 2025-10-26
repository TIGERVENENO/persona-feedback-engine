package ru.tigran.personafeedbackengine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "feedback_results", indexes = {
    @Index(name = "idx_feedback_result_session", columnList = "feedback_session_id"),
    @Index(name = "idx_feedback_result_product_persona", columnList = "product_id, persona_id"),
    @Index(name = "idx_feedback_result_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"feedbackSession", "product", "persona"})
public class FeedbackResult extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String feedbackText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeedbackResultStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_session_id", nullable = false)
    private FeedbackSession feedbackSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = FeedbackResultStatus.PENDING;
        }
    }

    public enum FeedbackResultStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED
    }
}
