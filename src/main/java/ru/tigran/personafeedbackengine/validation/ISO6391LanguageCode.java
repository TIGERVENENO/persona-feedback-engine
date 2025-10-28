package ru.tigran.personafeedbackengine.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Валидирует строку на соответствие стандарту ISO 639-1 (двухбуквенные коды языков).
 * Примеры валидных кодов: EN, RU, FR, DE, ES, и т.д.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ISO6391LanguageCodeValidator.class)
public @interface ISO6391LanguageCode {
    String message() default "Language code must be a valid ISO 639-1 two-letter code";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
