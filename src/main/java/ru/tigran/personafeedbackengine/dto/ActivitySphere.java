package ru.tigran.personafeedbackengine.dto;

/**
 * Enum for activity spheres / industries used in persona demographic validation.
 * Represents major industry sectors for persona generation.
 */
public enum ActivitySphere {
    IT("IT", "Information Technology"),
    FINANCE("FINANCE", "Finance & Banking"),
    HEALTHCARE("HEALTHCARE", "Healthcare & Medicine"),
    EDUCATION("EDUCATION", "Education"),
    RETAIL("RETAIL", "Retail & Commerce"),
    MANUFACTURING("MANUFACTURING", "Manufacturing"),
    CONSTRUCTION("CONSTRUCTION", "Construction"),
    ENTERTAINMENT("ENTERTAINMENT", "Entertainment & Media"),
    MEDIA("MEDIA", "Media & Publishing"),
    REAL_ESTATE("REAL_ESTATE", "Real Estate"),
    HOSPITALITY("HOSPITALITY", "Hospitality & Tourism"),
    TRANSPORTATION("TRANSPORTATION", "Transportation & Logistics"),
    ENERGY("ENERGY", "Energy & Utilities"),
    AGRICULTURE("AGRICULTURE", "Agriculture & Food"),
    TELECOMMUNICATIONS("TELECOMMUNICATIONS", "Telecommunications"),
    AUTOMOTIVE("AUTOMOTIVE", "Automotive"),
    FASHION("FASHION", "Fashion & Apparel"),
    SPORTS("SPORTS", "Sports & Fitness"),
    GOVERNMENT("GOVERNMENT", "Government & Public Service"),
    LAW("LAW", "Law & Legal Services"),
    CONSULTING("CONSULTING", "Consulting & Professional Services"),
    MARKETING("MARKETING", "Marketing & Advertising"),
    HUMAN_RESOURCES("HUMAN_RESOURCES", "Human Resources"),
    RESEARCH("RESEARCH", "Research & Development"),
    STUDENT("STUDENT", "Student"),
    UNEMPLOYED("UNEMPLOYED", "Unemployed / Job Seeker"),
    RETIRED("RETIRED", "Retired"),
    FREELANCE("FREELANCE", "Freelance / Self-employed"),
    OTHER("OTHER", "Other");

    private final String value;
    private final String displayName;

    ActivitySphere(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    public String getValue() {
        return value;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get ActivitySphere enum by string value (case-insensitive)
     * @param value the string value (e.g., "IT", "FINANCE", "HEALTHCARE")
     * @return ActivitySphere enum or throws IllegalArgumentException if not found
     */
    public static ActivitySphere fromValue(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Activity sphere value cannot be null or empty");
        }
        for (ActivitySphere sphere : ActivitySphere.values()) {
            if (sphere.value.equalsIgnoreCase(value)) {
                return sphere;
            }
        }
        throw new IllegalArgumentException("Invalid activity sphere value: " + value);
    }
}
