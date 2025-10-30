package ru.tigran.personafeedbackengine.dto;

/**
 * Enum for persona gender values.
 * Used for demographic validation and persona generation.
 */
public enum Gender {
    MALE("male", "Male"),
    FEMALE("female", "Female"),
    OTHER("other", "Non-binary / Other");

    private final String value;
    private final String displayName;

    Gender(String value, String displayName) {
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
     * Get Gender enum by string value (case-insensitive)
     * @param value the string value ("male", "female", "other")
     * @return Gender enum or throws IllegalArgumentException if not found
     */
    public static Gender fromValue(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Gender value cannot be null or empty");
        }
        for (Gender gender : Gender.values()) {
            if (gender.value.equalsIgnoreCase(value)) {
                return gender;
            }
        }
        throw new IllegalArgumentException("Invalid gender value: " + value);
    }
}
