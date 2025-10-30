package ru.tigran.personafeedbackengine.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.tigran.personafeedbackengine.dto.IncomeLevel;

/**
 * JPA converter for IncomeLevel enum to/from database.
 * Converts Java enum to PostgreSQL enum type.
 */
@Converter(autoApply = true)
public class IncomeLevelConverter implements AttributeConverter<IncomeLevel, String> {

    @Override
    public String convertToDatabaseColumn(IncomeLevel attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getValue();
    }

    @Override
    public IncomeLevel convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            return IncomeLevel.fromValue(dbData);
        } catch (IllegalArgumentException e) {
            return null;  // Return null if value doesn't match any enum
        }
    }
}
