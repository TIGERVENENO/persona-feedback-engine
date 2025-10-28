package ru.tigran.personafeedbackengine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "personas", indexes = {
    @Index(name = "idx_persona_user_deleted", columnList = "user_id,deleted"),
    @Index(name = "idx_persona_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "user")
public class Persona extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String detailedDescription;

    private String gender;

    private String ageGroup;

    private String race;

    private String avatarUrl;

    @Column(columnDefinition = "TEXT")
    private String productAttitudes;

    // Demographics fields for target audience matching and persona reuse
    @Column(length = 10)
    private String demographicGender;  // "male", "female", "other"

    @Column
    private Integer age;  // Exact age (e.g., 27, 34)

    @Column(length = 50)
    private String region;  // "moscow", "spb", "regions"

    @Column(length = 20)
    private String incomeLevel;  // "low", "medium", "high"

    @Column(columnDefinition = "JSONB")
    private String demographicsHash;  // JSON hash for uniqueness and caching

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PersonaStatus status;

    // Store the generation prompt for caching purposes
    @Column(columnDefinition = "TEXT")
    private String generationPrompt;

    // Soft delete: помечает удаленные персоны, но сохраняет feedback историю
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean deleted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public enum PersonaStatus {
        GENERATING, ACTIVE, FAILED
    }
}
