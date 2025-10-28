package ru.tigran.personafeedbackengine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tigran.personafeedbackengine.dto.PersonaVariation;
import ru.tigran.personafeedbackengine.dto.TargetAudience;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Сервис для генерации вариаций персон из целевой аудитории.
 *
 * Принцип работы:
 * - Получает TargetAudience (списки genders, ageRanges, regions, incomes)
 * - Генерирует N вариаций PersonaVariation (N = personaCount)
 * - Равномерно распределяет параметры по вариациям (round-robin)
 * - Конвертирует age ranges в конкретные возрасты
 *
 * Пример:
 * Input:
 *   targetAudience = {
 *     genders: ["male", "female"],
 *     ageRanges: ["25-35", "36-45"],
 *     regions: ["moscow"],
 *     incomes: ["medium", "high"]
 *   }
 *   personaCount = 5
 *
 * Output: 5 вариаций:
 *   1. {male, 28, moscow, medium}
 *   2. {female, 31, moscow, high}
 *   3. {male, 39, moscow, medium}
 *   4. {female, 42, moscow, high}
 *   5. {male, 27, moscow, medium}
 */
@Service
@Slf4j
public class PersonaVariationService {
    private final Random random = new Random();

    /**
     * Генерирует список вариаций персон из целевой аудитории.
     *
     * @param targetAudience Целевая аудитория с параметрами
     * @param personaCount   Количество вариаций для генерации (3-7)
     * @return Список PersonaVariation с равномерным распределением параметров
     */
    public List<PersonaVariation> generateVariations(TargetAudience targetAudience, int personaCount) {
        log.debug("Generating {} persona variations from target audience: {}", personaCount, targetAudience);

        List<PersonaVariation> variations = new ArrayList<>();

        for (int i = 0; i < personaCount; i++) {
            // Циклическое распределение параметров (round-robin)
            String gender = selectRoundRobin(targetAudience.genders(), i);
            String ageRange = selectRoundRobin(targetAudience.ageRanges(), i);
            String region = selectRoundRobin(targetAudience.regions(), i);
            String incomeLevel = selectRoundRobin(targetAudience.incomes(), i);

            // Конвертируем age range в конкретный возраст
            Integer age = parseAgeFromRange(ageRange);

            PersonaVariation variation = new PersonaVariation(gender, age, region, incomeLevel);
            variations.add(variation);

            log.debug("Generated variation #{}: {}", i + 1, variation);
        }

        log.info("Successfully generated {} persona variations", variations.size());
        return variations;
    }

    /**
     * Выбирает элемент из списка по индексу с циклическим повторением (round-robin).
     *
     * @param list  Список для выбора
     * @param index Текущий индекс
     * @return Элемент из списка (индекс % размер списка)
     */
    private <T> T selectRoundRobin(List<T> list, int index) {
        return list.get(index % list.size());
    }

    /**
     * Парсит возраст из age range и возвращает случайное значение в диапазоне.
     *
     * Поддерживаемые форматы:
     * - "18-25" -> random(18, 25)
     * - "26-35" -> random(26, 35)
     * - "36-45" -> random(36, 45)
     * - "46+" -> random(46, 65)
     *
     * @param ageRange Строка с диапазоном возраста
     * @return Конкретный возраст в диапазоне
     */
    private Integer parseAgeFromRange(String ageRange) {
        if (ageRange.endsWith("+")) {
            // "46+" -> 46-65
            int minAge = Integer.parseInt(ageRange.replace("+", ""));
            return randomAge(minAge, 65);
        } else if (ageRange.contains("-")) {
            // "18-25" -> 18-25
            String[] parts = ageRange.split("-");
            int minAge = Integer.parseInt(parts[0]);
            int maxAge = Integer.parseInt(parts[1]);
            return randomAge(minAge, maxAge);
        } else {
            // Если формат не распознан, возвращаем число как есть
            return Integer.parseInt(ageRange);
        }
    }

    /**
     * Генерирует случайный возраст в диапазоне [minAge, maxAge] включительно.
     */
    private int randomAge(int minAge, int maxAge) {
        return minAge + random.nextInt(maxAge - minAge + 1);
    }
}
