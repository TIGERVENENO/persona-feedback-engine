package ru.tigran.personafeedbackengine.dto;

/**
 * Income level enum for persona demographics.
 * Represents income classification: low, medium, high.
 */
public enum IncomeLevel {
    LOW("low", "Low Income"),
    MEDIUM("medium", "Medium Income"),
    HIGH("high", "High Income");

    private final String value;
    private final String displayName;

    IncomeLevel(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    public String getValue() {
        return value;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static IncomeLevel fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (IncomeLevel level : values()) {
            if (level.value.equalsIgnoreCase(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown income level: " + value);
    }
}
