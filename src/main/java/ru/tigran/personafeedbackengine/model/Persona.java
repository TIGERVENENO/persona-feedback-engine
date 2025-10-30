package ru.tigran.personafeedbackengine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;
import ru.tigran.personafeedbackengine.dto.IncomeLevel;
import ru.tigran.personafeedbackengine.config.IncomeLevelConverter;

@Entity
@Table(name = "personas", indexes = {
    @Index(name = "idx_persona_user_deleted", columnList = "user_id,deleted"),
    @Index(name = "idx_persona_status", columnList = "status"),
    @Index(name = "idx_persona_characteristics", columnList = "country,city,demographic_gender,activity_sphere,deleted")
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
    @Column(length = 2)
    private String country;  // ISO 3166-1 alpha-2 code (e.g., "RU", "US", "GB")

    @Column(length = 100)
    private String city;  // City name (e.g., "Moscow", "New York")

    @Column(length = 10)
    private String demographicGender;  // "male", "female", "other"

    @Column
    private Integer minAge;  // Minimum age from request

    @Column
    private Integer maxAge;  // Maximum age from request

    @Column
    private Integer age;  // Exact age generated for this persona

    @Column(length = 50)
    private String model;  // AI model used for generation (e.g., "claude-3-5-sonnet", "gpt-4o")

    @Column(length = 50)
    private String activitySphere;  // Activity sphere/industry (e.g., "IT", "FINANCE", "HEALTHCARE")

    @Column(length = 150)
    private String profession;  // Specific profession/role (e.g., "Senior Software Engineer")

    @Convert(converter = IncomeLevelConverter.class)
    @Column(length = 10)
    private IncomeLevel incomeLevel;  // Income classification (LOW, MEDIUM, HIGH)

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String interests;  // JSON array of interests/hobbies

    @Column(columnDefinition = "TEXT")
    private String additionalParams;  // Additional custom parameters (up to 500 chars)

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String characteristicsHash;  // JSON hash of all characteristics for caching and reuse

    // Legacy fields (for backward compatibility)
    @Column(length = 50)
    private String region;  // "moscow", "spb", "regions" - DEPRECATED: use city/country instead

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String demographicsHash;  // JSON hash for uniqueness and caching - DEPRECATED: use characteristicsHash

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PersonaStatus status;

    // Store the generation prompt for caching purposes
    @Column(columnDefinition = "TEXT")
    private String generationPrompt;

    // Prevents concurrent updates from multiple consumer threads (async race condition prevention)
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean generationInProgress = false;

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
