package ru.tigran.personafeedbackengine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Сервис для защиты от дублирующихся запросов через idempotency key
 * Предотвращает случайное выполнение одного и того же действия дважды
 */
@Slf4j
@Service
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(5);

    public IdempotencyService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Проверяет, является ли запрос дублирующимся
     * Первый вызов с таким ключом вернет false (не дублирующийся),
     * последующие вызовы вернут true (дублирующийся)
     *
     * @param idempotencyKey уникальный ключ для идентификации операции
     * @return true если это дублирующийся запрос, false если новый
     */
    public boolean isDuplicate(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn("Idempotency key is blank, treating as unique");
            return false;
        }

        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        // Пытаемся установить значение только если его нет
        Boolean wasSet = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", IDEMPOTENCY_TTL);

        if (wasSet == null || !wasSet) {
            log.warn("Duplicate request detected for idempotency key: {}", idempotencyKey);
            return true;
        }

        log.debug("New request with idempotency key: {}", idempotencyKey);
        return false;
    }

    /**
     * Явно удаляет идемпотентность ключ (для очистки)
     *
     * @param idempotencyKey ключ для удаления
     */
    public void removeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        Boolean deleted = redisTemplate.delete(redisKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("Removed idempotency key: {}", idempotencyKey);
        }
    }
}
