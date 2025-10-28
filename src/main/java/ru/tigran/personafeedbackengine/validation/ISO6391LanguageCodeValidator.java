package ru.tigran.personafeedbackengine.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Валидатор для проверки кодов языков по стандарту ISO 639-1.
 * Использует встроенный Java API Locale для получения списка всех доступных языков.
 */
public class ISO6391LanguageCodeValidator implements ConstraintValidator<ISO6391LanguageCode, String> {

    private static final Set<String> VALID_LANGUAGE_CODES = new HashSet<>();

    static {
        // Получаем все доступные языковые коды из Java Locale API
        String[] isoLanguages = Locale.getISOLanguages();
        for (String lang : isoLanguages) {
            VALID_LANGUAGE_CODES.add(lang.toUpperCase());
        }
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }

        // Приводим к верхнему регистру и проверяем длину
        String upperValue = value.toUpperCase();
        if (upperValue.length() != 2) {
            return false;
        }

        // Проверяем наличие в списке валидных ISO 639-1 кодов
        return VALID_LANGUAGE_CODES.contains(upperValue);
    }
}
