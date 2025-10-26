# ПОЛНЫЙ АНАЛИЗ АРХИТЕКТУРЫ: Persona Feedback Engine MVP

**Дата анализа:** 26 октября 2025
**Проект:** persona-feedback-engine
**Версия:** 0.0.1-SNAPSHOT (MVP)

---

## 📊 Краткое резюме

Проанализировал **35 Java файлов**, **13 CLAUDE.md**, **конфигурацию**, **схему БД** и **Docker setup**.

**Найдено проблем:** 50+

**Статус исправлений:**
- ✅ **Исправлено:** 8 критических проблем (Волна 1 + Волна 2)
- 🔴 **Ещё нужно:** 4 критических проблем
- 🟠 **Высокий приоритет:** 18 (блокируют будущие фичи)
- 🟡 **Средний приоритет:** 15 (производительность и качество кода)
- 🟢 **Низкий приоритет:** 7 (технический долг)

**Исправления Волны 1:**
- ✅ Redis TTL конфигурация (коммит: 33574dc)
- ✅ NPE при парсинге AI ответа (коммит: 5a339c3)
- ✅ Валидация статуса Persona (коммит: ef67805)
- ✅ Проверка пустых списков в DTOs (коммит: 0d70b8c)

**Исправления Волны 2:**
- ✅ N+1 Query Problem (коммит: cc879a8)
- ✅ Идемпотентность в consumers (коммит: e9e634b)
- ✅ Race Condition при обновлении сессии (коммит: 0ee4761)
- ✅ Dead Letter Queue конфигурация (коммит: 524411f)

---

## 🔴 КРИТИЧЕСКИЕ ПРОБЛЕМЫ (требуют немедленного исправления)

### 1. **Глобальное кеширование персон без учета userId**
**[ПЕРЕИМЕНОВАНО из #8 - исправлено #12]**
**Файл:** `AIGatewayService.java:67`

**Проблема:**
```java
@Cacheable(value = "personaCache", key = "#userPrompt")  // ❌ Кеш по prompt глобальный
public String generatePersonaDetails(String userPrompt) {
```

**Последствия:** Теоретически пользователь A может получить кешированную персону, созданную для пользователя B (если промпты совпадут). Нарушение изоляции данных.

**Решение:**
```java
// Добавить userId в параметры и в кеш-ключ
@Cacheable(value = "personaCache", key = "#userId + ':' + #userPrompt")
public String generatePersonaDetails(Long userId, String userPrompt) {
    // ...
}

// Обновить вызовы в PersonaTaskConsumer
String personaDetailsJson = aiGatewayService.generatePersonaDetails(
    persona.getUser().getId(),
    task.userPrompt()
);
```

---

### 2. **Поглощение исключений в consumers**
**[ПЕРЕИМЕНОВАНО из #9]**
**Файл:** `PersonaTaskConsumer.java:69-82`, `FeedbackTaskConsumer.java:104-131`

**Проблема:**
```java
} catch (Exception e) {
    log.error("Error processing persona generation task", e);
    // Исключение поглощено, RabbitMQ считает сообщение обработанным успешно
    // ❌ Нет throw, нет requeue
}
```

**Последствия:**
- RabbitMQ удалит сообщение из очереди
- Задача потеряна навсегда
- Нет автоматического retry

**Решение:**
```java
} catch (Exception e) {
    log.error("Error processing persona generation task for persona {}", task.personaId(), e);

    // Пометить как FAILED в БД
    markPersonaAsFailed(task.personaId());

    // Отправить в DLQ для анализа
    rabbitTemplate.convertAndSend(
        "dlx-exchange",
        "dlx.persona.generation",
        task
    );

    // ✅ Пробросить исключение для RabbitMQ (он отправит в DLQ автоматически)
    throw new AmqpRejectAndDontRequeueException("Failed to process persona generation", e);
}
```

---

### 3. **Отсутствие транзакций в controller**
**[ПЕРЕИМЕНОВАНО из #10]**
**Файл:** `FeedbackController.java:83-115`

**Проблема:**
```java
@GetMapping("/{sessionId}")
public ResponseEntity<FeedbackSessionResponse> getFeedbackSession(...) {
    FeedbackSession session = feedbackSessionRepository.findByUserIdAndId(...);
    List<FeedbackResult> results = feedbackResultRepository.findByFeedbackSessionId(...);
    // ❌ Два запроса без транзакции, могут вернуть inconsistent данные
}
```

**Последствия:** Между двумя запросами данные могут измениться.

**Решение:**
```java
@Transactional(readOnly = true)
@GetMapping("/{sessionId}")
public ResponseEntity<FeedbackSessionResponse> getFeedbackSession(...) {
    // ...
}
```

---

### 4. **Batch операции в цикле**
**[ПЕРЕИМЕНОВАНО из #11]**
**Файл:** `FeedbackService.java:115-139`

**Проблема:**
```java
for (Product product : products) {
    for (Persona persona : personas) {
        FeedbackResult result = new FeedbackResult();
        // ...
        FeedbackResult savedResult = feedbackResultRepository.save(result);  // ❌ Save в цикле

        rabbitTemplate.convertAndSend(...);  // ❌ Отправка в цикле
    }
}
```

**Последствия:** Для 5 продуктов × 5 персон = 25 отдельных INSERT + 25 отдельных RabbitMQ сообщений.

**Решение:**
```java
List<FeedbackResult> results = new ArrayList<>();
List<FeedbackGenerationTask> tasks = new ArrayList<>();

for (Product product : products) {
    for (Persona persona : personas) {
        FeedbackResult result = new FeedbackResult();
        result.setFeedbackSession(savedSession);
        result.setProduct(product);
        result.setPersona(persona);
        result.setStatus(FeedbackResult.FeedbackResultStatus.PENDING);
        results.add(result);
    }
}

// ✅ Batch insert
List<FeedbackResult> savedResults = feedbackResultRepository.saveAll(results);

// ✅ Batch publish (если поддерживается)
for (FeedbackResult savedResult : savedResults) {
    tasks.add(new FeedbackGenerationTask(
        savedResult.getId(),
        savedResult.getProduct().getId(),
        savedResult.getPersona().getId()
    ));
}

// Отправить все сразу
tasks.forEach(task -> rabbitTemplate.convertAndSend(
    RabbitMQConfig.EXCHANGE_NAME,
    "feedback.generation",
    task
));
```

---

## 🛡️ ПРОБЛЕМЫ БЕЗОПАСНОСТИ

### 5. **Небезопасная аутентификация через X-User-Id**
**[ПЕРЕИМЕНОВАНО из #13]**
**Файлы:** `PersonaController.java:25`, `FeedbackController.java:58,84`

**Проблема:** Любой может подделать заголовок `X-User-Id` и получить доступ к чужим данным.

**Критичность:** 🔴 КРИТИЧЕСКАЯ

**Решение для будущего (система подписок):**
```java
// 1. Добавить Spring Security + JWT
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/**").authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt());
        return http.build();
    }
}

// 2. Извлекать userId из JWT токена
@RestController
public class PersonaController {
    @PostMapping
    public ResponseEntity<JobResponse> generatePersona(
        @AuthenticationPrincipal Jwt jwt,  // ✅ JWT из токена
        @Valid @RequestBody PersonaGenerationRequest request
    ) {
        Long userId = Long.parseLong(jwt.getSubject());
        // ...
    }
}
```

---

### 6. **SQL Injection через enum в строковом виде**
**[ПЕРЕИМЕНОВАНО из #14]**
**Файл:** `FeedbackResultRepository.java:12`

**Проблема:**
```java
long countByFeedbackSessionIdAndStatus(Long feedbackSessionId, String status);
// ❌ String status может содержать SQL injection
```

**Решение:**
```java
// Использовать enum напрямую
long countByFeedbackSessionIdAndStatus(Long feedbackSessionId,
                                        FeedbackResultStatus status);

// Или явный @Query
@Query("SELECT COUNT(fr) FROM FeedbackResult fr " +
       "WHERE fr.feedbackSession.id = :sessionId AND fr.status = :status")
long countBySessionAndStatus(@Param("sessionId") Long sessionId,
                              @Param("status") FeedbackResultStatus status);
```

---

### 7. [ПЕРЕИМЕНОВАНО из #15] **Секреты в application.properties**
**Файл:** `application.properties:34,42`

**Проблема:** API ключи с placeholder значениями могут случайно попасть в git.

**Решение:**
```properties
# application.properties - только ссылки на env variables
app.openrouter.api-key=${OPENROUTER_API_KEY:}
app.agentrouter.api-key=${AGENTROUTER_API_KEY:}

# Добавить валидацию при старте
@Component
public class ApiKeyValidator implements ApplicationRunner {
    @Value("${app.openrouter.api-key}")
    private String openRouterKey;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (openRouterKey == null || openRouterKey.contains("YOUR_") || openRouterKey.isEmpty()) {
            throw new IllegalStateException(
                "OPENROUTER_API_KEY not configured! Please set it in environment variables."
            );
        }
    }
}
```

---

### 8. [ПЕРЕИМЕНОВАНО из #16] **Отсутствие rate limiting**

**Проблема:** Нет защиты от DDoS/abuse через множественные запросы.

**Решение для будущей системы подписок:**
```java
// Используя Bucket4j + Redis
@Configuration
public class RateLimitConfig {
    @Bean
    public Bucket createBucket() {
        Bandwidth limit = Bandwidth.builder()
            .capacity(100)
            .refillGreedy(100, Duration.ofMinutes(1))
            .build();
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }
}

@Component
public class RateLimitInterceptor extends HandlerInterceptorAdapter {
    private final Map<Long, Bucket> userBuckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        Long userId = getUserIdFromJWT(request);

        // Получить план подписки пользователя из БД
        SubscriptionPlan plan = subscriptionService.getUserPlan(userId);

        Bucket bucket = userBuckets.computeIfAbsent(userId,
            k -> createBucketForPlan(plan));

        if (!bucket.tryConsume(1)) {
            response.setStatus(429);
            return false;
        }
        return true;
    }
}
```

---

## ⚡ ПРОБЛЕМЫ МАСШТАБИРУЕМОСТИ

### 9. [ПЕРЕИМЕНОВАНО из #17] **Нет распределенной блокировки для обновления статуса сессии**

**Проблема:** При горизонтальном масштабировании (несколько инстансов приложения) несколько consumer'ов могут одновременно обновлять `FeedbackSession.status`.

**Решение:**
```java
// Используя Redisson
@Service
public class FeedbackTaskConsumer {
    private final RedissonClient redisson;

    @RabbitListener(queues = RabbitMQConfig.FEEDBACK_GENERATION_QUEUE)
    @Transactional
    public void consumeFeedbackTask(FeedbackGenerationTask task) {
        // ... обработка result

        // ✅ Распределенная блокировка при проверке завершения
        String lockKey = "feedback-session-lock:" + session.getId();
        RLock lock = redisson.getLock(lockKey);

        try {
            lock.lock(10, TimeUnit.SECONDS);

            // Проверка и обновление статуса под блокировкой
            long completedCount = feedbackResultRepository.countByFeedbackSessionIdAndStatus(
                session.getId(), FeedbackResultStatus.COMPLETED
            );
            long totalCount = feedbackResultRepository.countByFeedbackSessionId(session.getId());

            if (completedCount == totalCount) {
                session.setStatus(FeedbackSessionStatus.COMPLETED);
                feedbackSessionRepository.save(session);
            }
        } finally {
            lock.unlock();
        }
    }
}
```

---

### 10. [ПЕРЕИМЕНОВАНО из #18] **Отсутствие pagination**

**Проблема:** `findByFeedbackSessionId` может вернуть тысячи результатов.

**Решение:**
```java
// В repository
Page<FeedbackResult> findByFeedbackSessionId(Long sessionId, Pageable pageable);

// В controller
@GetMapping("/{sessionId}")
public ResponseEntity<FeedbackSessionResponse> getFeedbackSession(
    @RequestHeader("X-User-Id") Long userId,
    @PathVariable Long sessionId,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
) {
    Pageable pageable = PageRequest.of(page, size);
    Page<FeedbackResult> results = feedbackResultRepository
        .findByFeedbackSessionId(sessionId, pageable);
    // ...
}
```

---

### 11. [ПЕРЕИМЕНОВАНО из #19] **Блокирующие HTTP вызовы в consumers**

**Проблема:** `AIGatewayService` делает синхронные HTTP запросы (до 30 секунд), блокируя поток consumer.

**Решение:**
```java
// Использовать WebClient (асинхронный) вместо RestClient
@Service
public class AIGatewayService {
    private final WebClient webClient;

    public Mono<String> generatePersonaDetailsAsync(String userPrompt) {
        return webClient.post()
            .uri(apiUrl)
            .header("Authorization", "Bearer " + apiKey)
            .bodyValue(buildRequestBody(systemPrompt, userPrompt, model))
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(30))
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)));
    }
}

// В consumer использовать асинхронную обработку
@RabbitListener(queues = RabbitMQConfig.PERSONA_GENERATION_QUEUE,
                concurrency = "5-10")  // Динамическое масштабирование
public void consumePersonaTask(PersonaGenerationTask task) {
    aiGatewayService.generatePersonaDetailsAsync(task.userPrompt())
        .subscribe(personaDetailsJson -> {
            // Обработка ответа
            updatePersona(task.personaId(), personaDetailsJson);
        }, error -> {
            // Обработка ошибки
            markPersonaAsFailed(task.personaId(), error);
        });
}
```

---

### 12. [ПЕРЕИМЕНОВАНО из #20] **Отсутствие мониторинга и метрик**

**Проблема:** Нет Prometheus/Grafana интеграции для отслеживания производительности.

**Решение:**
```java
// pom.xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

// application.properties
management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.metrics.export.prometheus.enabled=true

// Кастомные метрики
@Service
public class PersonaService {
    private final Counter personaGenerationCounter;
    private final Timer personaGenerationTimer;

    public PersonaService(MeterRegistry registry) {
        this.personaGenerationCounter = Counter.builder("persona.generation.count")
            .tag("status", "initiated")
            .register(registry);
        this.personaGenerationTimer = Timer.builder("persona.generation.duration")
            .register(registry);
    }

    public Long startPersonaGeneration(Long userId, PersonaGenerationRequest request) {
        personaGenerationCounter.increment();
        return personaGenerationTimer.record(() -> {
            // ... логика
        });
    }
}
```

---

## 🔧 ПРОБЛЕМЫ ПРОИЗВОДИТЕЛЬНОСТИ

### 13. [ПЕРЕИМЕНОВАНО из #21] **Отсутствие индексов для часто используемых запросов**

**Файл:** `schema.sql`

**Проблема:** Нет составного индекса для `(user_id, status)` который часто используется.

**Решение:**
```sql
-- Добавить в schema.sql
CREATE INDEX idx_personas_user_status ON personas(user_id, status);
CREATE INDEX idx_feedback_results_session_product_persona
    ON feedback_results(feedback_session_id, product_id, persona_id);
CREATE INDEX idx_feedback_sessions_user_status ON feedback_sessions(user_id, status);
```

---

### 14. [ПЕРЕИМЕНОВАНО из #22] **Дублирование запросов к БД**

**Файл:** `FeedbackTaskConsumer.java:90-96, 134-138`

**Проблема:** `findByFeedbackSessionId` вызывается дважды для подсчета total count.

**Решение:**
```java
// Кешировать total count в памяти или в Redis
// Или использовать одну query с GROUP BY
@Query("SELECT NEW ru.tigran.personafeedbackengine.dto.SessionStatusInfo(" +
       "COUNT(CASE WHEN fr.status = 'COMPLETED' THEN 1 END), " +
       "COUNT(CASE WHEN fr.status = 'FAILED' THEN 1 END), " +
       "COUNT(*)) " +
       "FROM FeedbackResult fr WHERE fr.feedbackSession.id = :sessionId")
SessionStatusInfo getSessionStatus(@Param("sessionId") Long sessionId);

record SessionStatusInfo(long completed, long failed, long total) {}
```

---

### 15. [ПЕРЕИМЕНОВАНО из #23] **Отсутствие кеширования результатов сессий**

**Проблема:** GET `/feedback-sessions/{id}` может вызываться очень часто (polling), но результаты не кешируются.

**Решение:**
```java
@Service
public class FeedbackQueryService {

    @Cacheable(value = "feedbackSessions", key = "#userId + ':' + #sessionId")
    public FeedbackSessionResponse getFeedbackSessionCached(Long userId, Long sessionId) {
        // ... логика из controller
    }

    // Инвалидация кеша при обновлении
    @CacheEvict(value = "feedbackSessions", key = "#userId + ':' + #sessionId")
    public void invalidateSessionCache(Long userId, Long sessionId) {
        // Вызывается из FeedbackTaskConsumer при обновлении статуса
    }
}
```

---

### 16. [ПЕРЕИМЕНОВАНО из #24] **Отсутствие connection pooling конфигурации**

**Файл:** `application.properties`

**Проблема:** Нет настроек HikariCP (max pool size, timeouts).

**Решение:**
```properties
# HikariCP configuration
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=1200000
spring.datasource.hikari.connection-test-query=SELECT 1

# RabbitMQ prefetch
spring.rabbitmq.listener.simple.prefetch=10
spring.rabbitmq.listener.simple.concurrency=3
spring.rabbitmq.listener.simple.max-concurrency=10
```

---

## 📦 ПРОБЛЕМЫ С ДАННЫМИ И ТРАНЗАКЦИЯМИ

### 17. [ПЕРЕИМЕНОВАНО из #25] **Нет оптимистичной блокировки**

**Проблема:** Отсутствует `@Version` в моделях для предотвращения lost updates.

**Решение:**
```java
@Entity
@Table(name = "personas")
public class Persona {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version  // ✅ Добавить версионирование
    private Long version;

    // ... остальные поля
}

// То же для FeedbackSession, FeedbackResult, Product
```

---

### 18. [ПЕРЕИМЕНОВАНО из #26] **CASCADE ALL на User может быть опасным**

**Файл:** `User.java:26-33`

**Проблема:** При удалении пользователя удалятся все персоны, продукты и сессии, включая исторические feedback.

**Решение:**
```java
@Entity
public class User {
    // Изменить cascade стратегию или добавить soft delete
    @Column(nullable = false)
    private Boolean deleted = false;

    @OneToMany(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<Product> products;

    // Для реального удаления создать отдельный endpoint с подтверждением
}
```

---

### 19. [ПЕРЕИМЕНОВАНО из #27] **RESTRICT на Product/Persona блокирует удаление**

**Файл:** `schema.sql:147-148`

**Проблема:** Нельзя удалить продукт/персону, если есть feedback_results.

**Решение:**
```sql
-- Вариант 1: Soft delete
ALTER TABLE products ADD COLUMN deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE personas ADD COLUMN deleted BOOLEAN DEFAULT FALSE;

-- Вариант 2: Изменить на SET NULL
CONSTRAINT fk_feedback_results_product FOREIGN KEY (product_id)
    REFERENCES products(id) ON DELETE SET NULL,
CONSTRAINT fk_feedback_results_persona FOREIGN KEY (persona_id)
    REFERENCES personas(id) ON DELETE SET NULL

-- Вариант 3: Архивировать старые feedback_results перед удалением
```

---

## 💾 ПРОБЛЕМЫ С КЕШИРОВАНИЕМ

### 20. [ПЕРЕИМЕНОВАНО из #28] **Кеширование по prompt слишком специфично**

**Проблема:** Небольшое изменение в промпте (пробел в конце) создаст новый кеш-ключ.

**Решение:**
```java
@Service
public class AIGatewayService {

    @Cacheable(value = "personaCache", key = "#userId + ':' + T(ru.tigran.util.CacheKeyUtils).normalizePrompt(#userPrompt)")
    public String generatePersonaDetails(Long userId, String userPrompt) {
        // ...
    }
}

public class CacheKeyUtils {
    public static String normalizePrompt(String prompt) {
        return prompt.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
```

---

### 21. [ПЕРЕИМЕНОВАНО из #29] **Redis password пустой по умолчанию**

**Файл:** `application.properties:24`

**Решение:**
```properties
spring.data.redis.password=${REDIS_PASSWORD:}

# docker-compose.yml - требовать пароль
redis:
  command: redis-server --requirepass ${REDIS_PASSWORD}
```

---

## 🚨 ПРОБЛЕМЫ С ОБРАБОТКОЙ ОШИБОК

### 22. [ПЕРЕИМЕНОВАНО из #30] **Retry логика только для 429**

**Файл:** `AIGatewayService.java:148-156`

**Проблема:** Retry только для 429, но 502/503/504 тоже могут быть временными.

**Решение:**
```java
.onStatus(HttpStatusCode::isError, (request, response) -> {
    int statusCode = response.getStatusCode().value();

    // ✅ Расширенный список retriable ошибок
    if (statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504) {
        log.warn("Retriable error: {}, will retry", statusCode);
        throw new RetriableException("Retriable error: " + statusCode);
    }

    // Non-retriable ошибки
    log.error("{} API error: {} {}", provider, statusCode, response.getStatusText());
    throw new AIGatewayException(provider + " API error: " + statusCode, "AI_SERVICE_ERROR");
})
```

---

### 23. [ПЕРЕИМЕНОВАНО из #31] **Нет различия между retriable и non-retriable ошибками**

**Решение:**
```java
public abstract class BaseException extends RuntimeException {
    private final boolean retriable;

    protected BaseException(String message, boolean retriable) {
        super(message);
        this.retriable = retriable;
    }

    public boolean isRetriable() {
        return retriable;
    }
}

public class AIGatewayException extends BaseException {
    public AIGatewayException(String message, boolean retriable) {
        super(message, retriable);
    }
}

// В consumers
} catch (Exception e) {
    if (e instanceof BaseException && ((BaseException) e).isRetriable()) {
        // Requeue для retry
        throw new AmqpRejectAndDontRequeueException("Will retry via DLQ", e);
    } else {
        // Не retry, сразу FAILED
        markAsFailed();
    }
}
```

---

## 🎯 ПРОБЛЕМЫ С КОДОМ И ПАТТЕРНАМИ

### 24. [ПЕРЕИМЕНОВАНО из #32] **Нарушение Single Responsibility**

**Файл:** `PersonaTaskConsumer.java`

**Проблема:** Consumer делает и парсинг JSON, и обновление БД, и логику состояния.

**Решение:**
```java
@Service
public class PersonaProcessingService {
    public PersonaDetails parseAndValidate(String json) {
        // Парсинг и валидация
    }

    @Transactional
    public void updatePersona(Long personaId, PersonaDetails details) {
        // Обновление БД
    }
}

@Service
public class PersonaTaskConsumer {
    private final PersonaProcessingService processingService;

    @RabbitListener(queues = RabbitMQConfig.PERSONA_GENERATION_QUEUE)
    public void consumePersonaTask(PersonaGenerationTask task) {
        PersonaDetails details = processingService.parseAndValidate(personaDetailsJson);
        processingService.updatePersona(task.personaId(), details);
    }
}
```

---

### 25. [ПЕРЕИМЕНОВАНО из #33] **Дублирование кода в error handling**

**Решение:**
```java
@Component
public class ConsumerErrorHandler {

    public <T> void handleError(Long entityId, Exception e,
                                 Consumer<Long> markFailedCallback) {
        log.error("Error processing task for entity {}", entityId, e);

        try {
            markFailedCallback.accept(entityId);
            log.warn("Marked entity {} as FAILED", entityId);
        } catch (Exception innerE) {
            log.error("Failed to mark entity as FAILED", innerE);
        }

        throw new AmqpRejectAndDontRequeueException("Processing failed", e);
    }
}

// Использование
@Service
public class PersonaTaskConsumer {
    private final ConsumerErrorHandler errorHandler;

    @RabbitListener(queues = RabbitMQConfig.PERSONA_GENERATION_QUEUE)
    public void consumePersonaTask(PersonaGenerationTask task) {
        try {
            // ... обработка
        } catch (Exception e) {
            errorHandler.handleError(task.personaId(), e, this::markPersonaAsFailed);
        }
    }
}
```

---

### 26. [ПЕРЕИМЕНОВАНО из #34] **Отсутствие DTO validation**

**Файл:** `PersonaGenerationRequest.java`, `FeedbackSessionRequest.java`

**Решение:**
```java
public record PersonaGenerationRequest(
    @NotBlank(message = "Prompt cannot be blank")
    @Size(max = 2000, message = "Prompt exceeds maximum length of 2000 characters")
    String prompt
) {}

public record FeedbackSessionRequest(
    @NotEmpty(message = "Product IDs cannot be empty")
    @Size(min = 1, max = 5, message = "Must have 1-5 products per session")
    List<@Positive Long> productIds,

    @NotEmpty(message = "Persona IDs cannot be empty")
    @Size(min = 1, max = 5, message = "Must have 1-5 personas per session")
    List<@Positive Long> personaIds
) {}
```

---

### 31. [ПЕРЕИМЕНОВАНО из #35] **Magic strings для error codes**

**Решение:**
```java
public enum ErrorCode {
    PERSONA_NOT_FOUND("PERSONA_NOT_FOUND", "Persona not found"),
    PRODUCT_NOT_FOUND("PRODUCT_NOT_FOUND", "Product not found"),
    USER_NOT_FOUND("USER_NOT_FOUND", "User not found"),
    SESSION_NOT_FOUND("SESSION_NOT_FOUND", "Feedback session not found"),
    UNAUTHORIZED_ACCESS("UNAUTHORIZED_ACCESS", "Unauthorized access"),
    VALIDATION_ERROR("VALIDATION_ERROR", "Validation failed"),
    INVALID_PROMPT_LENGTH("INVALID_PROMPT_LENGTH", "Prompt exceeds maximum length"),
    TOO_MANY_PRODUCTS("TOO_MANY_PRODUCTS", "Too many products"),
    TOO_MANY_PERSONAS("TOO_MANY_PERSONAS", "Too many personas"),
    AI_SERVICE_ERROR("AI_SERVICE_ERROR", "AI service error"),
    PERSONAS_NOT_READY("PERSONAS_NOT_READY", "Some personas are not ready");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}

// Использование
throw new ValidationException(
    "Prompt exceeds maximum length",
    ErrorCode.INVALID_PROMPT_LENGTH
);
```

---

### 32. [ПЕРЕИМЕНОВАНО из #36] **Lombok @Data на entities**

**Файл:** Все entity классы

**Проблема:** @Data генерирует equals/hashCode/toString которые могут вызвать LazyInitializationException.

**Решение:**
```java
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"user", "feedbackResults"})  // Исключить lazy поля
@EqualsAndHashCode(of = "id")  // Только по id
public class Persona {
    // ...
}
```

---

## ⚙️ ПРОБЛЕМЫ КОНФИГУРАЦИИ

### 33. [ПЕРЕИМЕНОВАНО из #37] **Жестко захардкоженные значения**

**Файл:** `AIGatewayService.java:26`

**Решение:**
```java
@Value("${app.ai.max-retries:3}")
private int maxRetries;

@Value("${app.ai.retry-backoff-multiplier:2}")
private int retryBackoffMultiplier;
```

---

### 34. [ПЕРЕИМЕНОВАНО из #38] **Hibernate ddl-auto=update опасно**

**Файл:** `application.properties:10`

**Решение:**
```properties
# Для production
spring.jpa.hibernate.ddl-auto=validate  # Только проверка, не изменения

# Использовать Flyway/Liquibase для миграций
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

---

### 35. [ПЕРЕИМЕНОВАНО из #39] **show-sql=true в production**

**Файл:** `application.properties:11`

**Решение:**
```properties
# Использовать profile-specific настройки
# application-prod.properties
spring.jpa.show-sql=false
logging.level.org.hibernate.SQL=WARN
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=WARN
```

---

### 36. [ПЕРЕИМЕНОВАНО из #40] **Нет graceful shutdown**

**Решение:**
```properties
# application.properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s

# RabbitMQ graceful shutdown
spring.rabbitmq.listener.simple.acknowledge-mode=auto
spring.rabbitmq.listener.simple.default-requeue-rejected=true
```

---

## 🌍 РЕКОМЕНДАЦИИ ДЛЯ БУДУЩИХ ФИЧ

### Для Умного Роутинга:

```java
// 1. Создать интерфейс для провайдеров
public interface AIProvider {
    String generatePersona(String prompt);
    String generateFeedback(String persona, String product);
    boolean isAvailable();
    int getCostPerRequest();
}

// 2. Реализации для разных провайдеров
@Service
public class OpenRouterProvider implements AIProvider {
    // ...
}

@Service
public class AgentRouterProvider implements AIProvider {
    // ...
}

// 3. Smart Router
@Service
public class AIProviderRouter {
    private final List<AIProvider> providers;
    private final LoadBalancer loadBalancer;

    public String generatePersona(String prompt, RoutingStrategy strategy) {
        AIProvider provider = switch (strategy) {
            case CHEAPEST -> providers.stream()
                .min(Comparator.comparingInt(AIProvider::getCostPerRequest))
                .orElseThrow();
            case FASTEST -> loadBalancer.selectLeastLoaded(providers);
            case ROUND_ROBIN -> loadBalancer.roundRobin(providers);
            case FALLBACK -> tryProvidersWithFallback(providers);
        };

        return provider.generatePersona(prompt);
    }
}
```

---

### Для Адаптации к Языку:

```java
// 1. Добавить locale в User
@Entity
public class User {
    @Column(nullable = false)
    private String locale = "en";  // en, ru, de, etc.
}

// 2. i18n для промптов
@Service
public class PromptTemplateService {
    @Autowired
    private MessageSource messageSource;

    public String getPersonaPrompt(Locale locale) {
        return messageSource.getMessage(
            "prompt.persona.generation",
            null,
            locale
        );
    }
}

// 3. Передавать язык в AI
@Service
public class AIGatewayService {
    public String generatePersonaDetails(Long userId, String userPrompt, Locale locale) {
        String systemPrompt = String.format(
            "Generate persona in %s language. Return JSON with keys: nm, dd, g, ag, r, au",
            locale.getDisplayLanguage(Locale.ENGLISH)
        );
        // ...
    }
}
```

---

### Для Системы Подписок:

```java
// 1. Модель подписки
@Entity
@Table(name = "subscriptions")
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private SubscriptionPlan plan;  // FREE, BASIC, PRO, ENTERPRISE

    private LocalDateTime validUntil;

    @Column(nullable = false)
    private Integer monthlyQuota;  // Запросов в месяц

    @Column(nullable = false)
    private Integer usedQuota = 0;
}

// 2. Проверка квоты перед запросом
@Service
public class QuotaService {

    @Transactional
    public void checkAndConsumeQuota(Long userId) {
        Subscription sub = subscriptionRepository.findActiveByUserId(userId)
            .orElseThrow(() -> new QuotaExceededException("No active subscription"));

        if (sub.getUsedQuota() >= sub.getMonthlyQuota()) {
            throw new QuotaExceededException("Monthly quota exceeded");
        }

        sub.setUsedQuota(sub.getUsedQuota() + 1);
        subscriptionRepository.save(sub);
    }
}

// 3. Интеграция в controllers
@PostMapping
public ResponseEntity<JobResponse> generatePersona(
    @AuthenticationPrincipal Jwt jwt,
    @Valid @RequestBody PersonaGenerationRequest request
) {
    Long userId = Long.parseLong(jwt.getSubject());

    quotaService.checkAndConsumeQuota(userId);  // ✅ Проверка квоты

    Long personaId = personaService.startPersonaGeneration(userId, request);
    // ...
}
```

---

## 📋 ПЛАН ДЕЙСТВИЙ ПО ПРИОРИТЕТАМ

### ⚡ Немедленно (Критические - 1-2 недели):

1. ✅ **Исправить Redis TTL конфигурацию** (CacheConfig.java)
2. ✅ **Добавить валидацию статуса персон** (FeedbackService.java)
3. ✅ **Исправить N+1 запрос** (FeedbackController.java)
4. ✅ **Добавить проверку на null при парсинге AI ответа** (PersonaTaskConsumer.java)
5. ✅ **Добавить идемпотентность в consumers**
6. ✅ **Добавить Dead Letter Queue**
7. ✅ **Исправить race condition при обновлении сессии**

### 🔒 Безопасность (2-3 недели):

8. ✅ **Внедрить JWT аутентификацию** вместо X-User-Id
9. ✅ **Добавить rate limiting**
10. ✅ **Вынести секреты в environment variables с валидацией**
11. ✅ **Исправить SQL injection риск** (enum вместо String)

### 📈 Масштабируемость (3-4 недели):

12. ✅ **Добавить распределенную блокировку** (Redisson)
13. ✅ **Внедрить pagination**
14. ✅ **Перейти на асинхронные HTTP вызовы** (WebClient)
15. ✅ **Настроить мониторинг** (Prometheus + Grafana)
16. ✅ **Добавить connection pooling конфигурацию**

### ⚡ Производительность (2 недели):

17. ✅ **Добавить составные индексы в БД**
18. ✅ **Оптимизировать batch операции** (saveAll вместо save в цикле)
19. ✅ **Добавить кеширование результатов сессий**
20. ✅ **Убрать дублирование запросов к БД**

### 🧩 Рефакторинг (2-3 недели):

21. ✅ **Разделить ответственности в consumers** (SRP)
22. ✅ **Добавить DTO validation**
23. ✅ **Заменить magic strings на enum** (ErrorCode)
24. ✅ **Исправить Lombok на entities** (@Data → @Getter/@Setter/@EqualsAndHashCode)
25. ✅ **Добавить @Version для оптимистичной блокировки**

### 🌍 Подготовка к будущим фичам (4-6 недель):

26. ✅ **Создать архитектуру для умного роутинга** (интерфейс AIProvider + роутер)
27. ✅ **Добавить i18n инфраструктуру** (locale в User, MessageSource)
28. ✅ **Спроектировать систему подписок** (Subscription entity, QuotaService)
29. ✅ **Настроить graceful shutdown**
30. ✅ **Добавить Flyway для миграций БД**

---

## 📊 ФИНАЛЬНАЯ ОЦЕНКА

**Общее состояние проекта:** 7/10

**Сильные стороны:**
- ✅ Хорошая структура кода (разделение по пакетам)
- ✅ Использование современных технологий (Spring Boot 3, Java 21)
- ✅ Асинхронная архитектура (RabbitMQ)
- ✅ Docker setup
- ✅ Отличная документация (CLAUDE.md)

**Критические проблемы:**
- ❌ Race conditions при параллельной обработке
- ❌ N+1 запросы
- ❌ Некорректная конфигурация кеша
- ❌ Небезопасная аутентификация
- ❌ Отсутствие идемпотентности

**Готовность к production:** 4/10 (требует исправления критических проблем)

**Готовность к масштабированию:** 5/10 (нужна работа над горизонтальным масштабированием)

---

## 🎯 ДОПОЛНИТЕЛЬНЫЕ ПРОБЛЕМЫ (41-50)

### 37. [ПЕРЕИМЕНОВАНО из #41] **Отсутствие аудита изменений**

**Проблема:** Нет логирования кто, когда и что изменил.

**Решение:**
```java
@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
public abstract class AuditableEntity {
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(updatable = false)
    private Long createdBy;

    @LastModifiedBy
    private Long lastModifiedBy;
}

// Все entities наследуют от AuditableEntity
@Entity
public class Persona extends AuditableEntity {
    // ...
}
```

---

### 38. [ПЕРЕИМЕНОВАНО из #42] **Отсутствие health checks для внешних зависимостей**

**Решение:**
```java
@Component
public class AIProviderHealthIndicator implements HealthIndicator {
    private final AIGatewayService aiGatewayService;

    @Override
    public Health health() {
        try {
            aiGatewayService.ping();
            return Health.up().build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

---

### 39. [ПЕРЕИМЕНОВАНО из #43] **Нет circuit breaker для AI API**

**Решение:**
```java
// pom.xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>

@Service
public class AIGatewayService {
    private final CircuitBreakerFactory circuitBreakerFactory;

    @CircuitBreaker(name = "aiProvider", fallbackMethod = "fallbackPersonaGeneration")
    public String generatePersonaDetails(String userPrompt) {
        // ... вызов AI
    }

    private String fallbackPersonaGeneration(String userPrompt, Exception e) {
        log.error("AI Provider down, using fallback", e);
        return "{}"; // Или другой fallback механизм
    }
}
```

---

### 40. [ПЕРЕИМЕНОВАНО из #44] **Отсутствие bulkhead pattern для изоляции**

**Проблема:** Если генерация персон зависает, это не должно блокировать генерацию feedback.

**Решение:**
```java
@Configuration
public class ThreadPoolConfig {

    @Bean(name = "personaGenerationExecutor")
    public Executor personaGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("persona-gen-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "feedbackGenerationExecutor")
    public Executor feedbackGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("feedback-gen-");
        executor.initialize();
        return executor;
    }
}

@RabbitListener(queues = RabbitMQConfig.PERSONA_GENERATION_QUEUE,
                executor = "personaGenerationExecutor")
public void consumePersonaTask(PersonaGenerationTask task) {
    // ...
}
```

---

### 41. [ПЕРЕИМЕНОВАНО из #45] **Нет версионирования API**

**Проблема:** API endpoints не версионированы.

**Решение:**
```java
@RestController
@RequestMapping("/api/v2/personas")  // Добавить v2 при изменениях
public class PersonaControllerV2 {
    // Новая версия API
}

// Или через header versioning
@GetMapping(value = "/personas", headers = "X-API-Version=2")
public ResponseEntity<?> getPersonasV2() {
    // ...
}
```

---

### 42. [ПЕРЕИМЕНОВАНО из #46] **Отсутствие CORS конфигурации**

**Решение:**
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("https://yourdomain.com")
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

---

### 43. [ПЕРЕИМЕНОВАНО из #47] **Нет обработки дублирующихся запросов**

**Проблема:** Пользователь может дважды нажать кнопку и создать две одинаковые сессии.

**Решение:**
```java
// Добавить идемпотентность через idempotency key
public record FeedbackSessionRequest(
    @NotNull String idempotencyKey,
    List<Long> productIds,
    List<Long> personaIds
) {}

@Service
public class IdempotencyService {
    private final RedisTemplate<String, String> redisTemplate;

    public boolean isDuplicate(String key) {
        String redisKey = "idempotency:" + key;
        return redisTemplate.opsForValue()
            .setIfAbsent(redisKey, "1", Duration.ofMinutes(5)) == Boolean.FALSE;
    }
}

@PostMapping
public ResponseEntity<JobResponse> startFeedbackSession(...) {
    if (idempotencyService.isDuplicate(request.idempotencyKey())) {
        throw new ValidationException("Duplicate request", "DUPLICATE_REQUEST");
    }
    // ...
}
```

---

### 44. [ПЕРЕИМЕНОВАНО из #48] **Нет логирования запросов/ответов**

**Решение:**
```java
@Component
@Slf4j
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("Request: {} {} - Status: {} - Duration: {}ms",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                duration
            );
        }
    }
}
```

---

### 45. [ПЕРЕИМЕНОВАНО из #49] **Отсутствие обработки timeout в AI запросах**

**Проблема:** 30 секунд timeout слишком долго для пользователя.

**Решение:**
```java
// Разделить на sync (быстрый) и async (медленный) endpoints
@PostMapping("/personas/sync")
public ResponseEntity<PersonaResponse> generatePersonaSync(...) {
    // Быстрая генерация с timeout 5 секунд
}

@PostMapping("/personas/async")
public ResponseEntity<JobResponse> generatePersonaAsync(...) {
    // Асинхронная генерация с polling
}

// В AIGatewayService
@Value("${app.ai.sync-timeout-seconds:5}")
private int syncTimeout;

@Value("${app.ai.async-timeout-seconds:30}")
private int asyncTimeout;
```

---

### 46. [ПЕРЕИМЕНОВАНО из #50] **Нет механизма откатов (rollback) при частичных сбоях**

**Проблема:** Если из 10 feedback результатов 9 успешны, а 1 упал - нет механизма отката.

**Решение:**
```java
// Использовать Saga pattern
@Service
public class FeedbackSagaOrchestrator {

    public void executeFeedbackSession(FeedbackSession session) {
        List<CompensatingAction> compensations = new ArrayList<>();

        try {
            for (FeedbackResult result : session.getFeedbackResults()) {
                processResult(result);
                compensations.add(() -> rollbackResult(result));
            }
        } catch (Exception e) {
            // Откатить все успешные операции в обратном порядке
            Collections.reverse(compensations);
            compensations.forEach(CompensatingAction::execute);
            throw e;
        }
    }
}
```

---

## 📝 ЗАКЛЮЧЕНИЕ

Проект имеет **солидную основу** для MVP, но требует значительных улучшений перед production deployment. Основные области для работы:

1. **Безопасность** - критична для системы подписок
2. **Масштабируемость** - важна для роста
3. **Надежность** - необходима для стабильности
4. **Производительность** - влияет на UX

**Рекомендуемый порядок действий:**
1. Исправить критические проблемы (1-2 недели)
2. Внедрить безопасность и аутентификацию (2-3 недели)
3. Улучшить масштабируемость и производительность (3-4 недели)
4. Провести рефакторинг и подготовку к будущим фичам (4-6 недель)

**Общее время до готовности к production:** 10-15 недель

---

**Анализ выполнен:** 26.10.2025
**Автор:** Claude (Anthropic)
