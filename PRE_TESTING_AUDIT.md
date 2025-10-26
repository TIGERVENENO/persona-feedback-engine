# Отчет по проверке проекта перед тестированием

**Дата проверки:** 2025-10-26
**Проект:** Persona Feedback Engine
**Версия:** 0.0.1-SNAPSHOT

---

## Резюме

Проведена полная проверка проекта перед началом тестирования. Выявлено **11 проблем** различного приоритета, которые могут негативно повлиять на тестирование или разработку.

### Статистика проблем

- 🔴 **Критические:** 3
- 🟡 **Средние:** 5
- 🟢 **Низкие:** 3

---

## 🔴 Критические проблемы

### 1. Несоответствие schema.sql и реальной модели данных

**Файл:** `src/main/resources/schema.sql`
**Приоритет:** 🔴 КРИТИЧЕСКИЙ

**Проблема:**
В `schema.sql` таблица `users` создается с полем `username`:
```sql
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    ...
);
```

Однако в реальной модели `User.java` поле `username` отсутствует - используется только `email` для аутентификации.

**Последствия:**
- Миграция Flyway `V2__Add_Authentication_Columns.sql` может не применяться корректно
- При запуске `docker-compose` с монтированием `schema.sql` создастся устаревшая структура БД
- Возможны ошибки при запуске приложения из-за несоответствия схемы и JPA entities

**Рекомендация:**
Удалить поле `username` из `schema.sql` и оставить только `email`, либо обновить `schema.sql` в соответствии с финальной структурой после всех миграций Flyway.

**Затронутые файлы:**
- `src/main/resources/schema.sql:18`
- `src/main/java/ru/tigran/personafeedbackengine/model/User.java`

---

### 2. Отсутствуют коды ошибок в ErrorCode enum

**Файл:** `src/main/java/ru/tigran/personafeedbackengine/exception/ErrorCode.java`
**Приоритет:** 🔴 КРИТИЧЕСКИЙ

**Проблема:**
В `AuthenticationService.java` используются строковые литералы для кодов ошибок, которые не определены в `ErrorCode.java`:

```java
// AuthenticationService.java:54
throw new ValidationException("Email already registered", "EMAIL_ALREADY_EXISTS");

// AuthenticationService.java:92
throw new ValidationException("Invalid email or password", "INVALID_CREDENTIALS");

// AuthenticationService.java:101
throw new ValidationException("User account is inactive or deleted", "USER_INACTIVE");
```

Эти коды отсутствуют в enum `ErrorCode`.

**Последствия:**
- Нарушение принципа централизации кодов ошибок
- Потенциальные опечатки в коде (компилятор не поймает ошибку)
- Клиенты API получают недокументированные коды ошибок
- Нарушается паттерн, описанный в `exception/CLAUDE.md`

**Рекомендация:**
Добавить недостающие коды в `ErrorCode.java`:
```java
EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", "Email already registered"),
INVALID_CREDENTIALS("INVALID_CREDENTIALS", "Invalid email or password"),
USER_INACTIVE("USER_INACTIVE", "User account is inactive or deleted"),
```

И обновить `AuthenticationService.java` для использования enum вместо строковых литералов.

**Затронутые файлы:**
- `src/main/java/ru/tigran/personafeedbackengine/exception/ErrorCode.java`
- `src/main/java/ru/tigran/personafeedbackengine/service/AuthenticationService.java:54,92,101`

---

### 3. Устаревшая документация в repository/CLAUDE.md

**Файл:** `src/main/java/ru/tigran/personafeedbackengine/repository/CLAUDE.md`
**Приоритет:** 🔴 КРИТИЧЕСКИЙ

**Проблема:**
В `repository/CLAUDE.md` задокументирован метод `findByUsername(String username)` для `UserRepository`:

```markdown
### UserRepository
- Extends `JpaRepository<User, Long>`
- Custom methods: `findByUsername(String username)`
```

Но в реальном коде `UserRepository.java` этот метод отсутствует - вместо него используется `findByEmail(String email)`.

**Последствия:**
- Разработчик, полагающийся на документацию, будет искать несуществующий метод
- Нарушение требования CLAUDE.md: "КРИТИЧНО: Обновляй CLAUDE.md при изменениях в коде!"
- Потеря доверия к документации

**Рекомендация:**
Обновить `repository/CLAUDE.md`:
```markdown
### UserRepository
- Extends `JpaRepository<User, Long>`
- Custom methods:
  - `findByEmail(String email)` - Find user by email address
  - `existsByEmail(String email)` - Check if email already registered
```

**Затронутые файлы:**
- `src/main/java/ru/tigran/personafeedbackengine/repository/CLAUDE.md:10`
- `src/main/java/ru/tigran/personafeedbackengine/repository/UserRepository.java`

---

## 🟡 Средние проблемы

### 4. Неполная документация модели User в model/CLAUDE.md

**Файл:** `src/main/java/ru/tigran/personafeedbackengine/model/CLAUDE.md`
**Приоритет:** 🟡 СРЕДНИЙ

**Проблема:**
В `model/CLAUDE.md` описание сущности `User` минимально:
```markdown
### User
- Represents a platform user/marketer
- Root entity for data isolation
- Cascades ALL operations to owned Personas and Products
```

Отсутствует описание полей:
- `email` (уникальный идентификатор для аутентификации)
- `passwordHash` (хеш пароля BCrypt)
- `isActive` (флаг активности пользователя)
- `deleted` (флаг мягкого удаления)

**Последствия:**
- Неполная картина модели данных
- Не очевидна роль `email` как основного идентификатора (вместо username)

**Рекомендация:**
Дополнить описание User в `model/CLAUDE.md`:
```markdown
### User
- Represents a platform user/marketer
- Root entity for data isolation
- Fields:
  - `email` - Unique user email (used for authentication instead of username)
  - `passwordHash` - BCrypt hashed password (60 chars)
  - `isActive` - Account active status (default: true)
  - `deleted` - Soft delete flag (default: false)
- Cascades PERSIST and MERGE to owned Personas, Products, FeedbackSessions
```

**Затронутые файлы:**
- `src/main/java/ru/tigran/personafeedbackengine/model/CLAUDE.md:8-11`

---

### 5. Отсутствует CLAUDE.md для директории util/

**Файл:** Отсутствует `src/main/java/ru/tigran/personafeedbackengine/util/CLAUDE.md`
**Приоритет:** 🟡 СРЕДНИЙ

**Проблема:**
В проекте существует директория `util/` с классом `CacheKeyUtils.java`, но для нее не создан файл `CLAUDE.md`.

Согласно глобальным правилам из `~/.claude/CLAUDE.md`:
> **В КАЖДОЙ директории src/ ДОЛЖЕН быть файл CLAUDE.md**

**Последствия:**
- Нарушение собственных стандартов документации
- Неясно назначение и использование утилит в `util/`

**Рекомендация:**
Создать `src/main/java/ru/tigran/personafeedbackengine/util/CLAUDE.md` с описанием:
- Назначения директории
- Описания класса `CacheKeyUtils` (методы, параметры, примеры использования)

**Затронутые файлы:**
- Отсутствует: `src/main/java/ru/tigran/personafeedbackengine/util/CLAUDE.md`

---

### 6. JWT Secret Key по умолчанию небезопасен

**Файл:** `src/main/resources/application.properties`
**Приоритет:** 🟡 СРЕДНИЙ

**Проблема:**
Дефолтное значение JWT secret key слишком длинное и содержит текст-подсказку:
```properties
app.jwt.secret-key=${JWT_SECRET_KEY:your-secret-key-please-change-this-in-production-at-least-32-chars}
```

Хотя в комментарии указано "Change this in production!", дефолтное значение может быть использовано по ошибке.

**Последствия:**
- Риск использования предсказуемого ключа в development/staging окружении
- Возможность подделки JWT токенов, если дефолтный ключ попадет в production

**Рекомендация:**
Использовать более безопасный подход:
1. Не указывать дефолтное значение вообще - пусть приложение не запустится без настройки
2. Или использовать случайную строку для development окружения
3. Добавить валидацию на старте приложения (как в `ApiKeyValidator`)

**Затронутые файлы:**
- `src/main/resources/application.properties:105`

---

### 7. Flyway миграция V1 названа некорректно

**Файл:** `src/main/resources/db/migration/V1__Initial_Schema.sql`
**Приоритет:** 🟡 СРЕДНИЙ

**Проблема:**
Файл миграции называется `V1__Initial_Schema.sql`, но содержит не создание начальной схемы, а добавление поддержки soft delete:

```sql
-- Миграция для soft delete поддержки
-- Добавляет колону deleted ко всем основным таблицам

ALTER TABLE users ADD COLUMN deleted BOOLEAN DEFAULT FALSE NOT NULL;
...
```

Это означает, что:
1. Начальная схема должна была быть создана раньше (возможно, через `schema.sql`)
2. Названия миграций не соответствуют их содержимому

**Последствия:**
- Путаница: название обещает "Initial Schema", а содержимое - добавление колонок
- Неясна логика создания базовой структуры БД (Flyway или schema.sql?)
- При чистой установке миграция V1 упадет, если не будет базовых таблиц

**Рекомендация:**
Переименовать миграцию:
- `V1__Add_Soft_Delete_Support.sql`

Или создать настоящую `V0__Initial_Schema.sql` с командами CREATE TABLE из schema.sql.

**Затронутые файлы:**
- `src/main/resources/db/migration/V1__Initial_Schema.sql`

---

### 8. Docker Compose ссылается на отсутствующие SQL файлы

**Файл:** `docker-compose.yml`
**Приоритет:** 🟡 СРЕДНИЙ

**Проблема:**
В `docker-compose.yml` в секции PostgreSQL указаны volume mappings для инициализации БД:

```yaml
volumes:
  # Initialize database schema on first startup
  - ./src/main/resources/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql
  - ./src/main/resources/data.sql:/docker-entrypoint-initdb.d/02-data.sql
```

Это означает, что при запуске `docker-compose up` PostgreSQL выполнит `schema.sql` и `data.sql` ДО запуска приложения Spring Boot с Flyway.

**Последствия:**
- Конфликт между `schema.sql` (устаревшая структура с username) и Flyway миграциями
- Flyway может попытаться применить миграции на уже существующую структуру из schema.sql
- Несоответствие между структурой БД в Docker и в чистой установке

**Рекомендация:**
Выбрать один из подходов:
1. **Использовать только Flyway:** Убрать volume mappings из docker-compose.yml, удалить schema.sql и data.sql
2. **Использовать только schema.sql:** Отключить Flyway (`spring.flyway.enabled=false`)
3. **Обновить schema.sql:** Привести в соответствие с финальной структурой после всех миграций

Рекомендуется первый вариант (только Flyway) для консистентности.

**Затронутые файлы:**
- `docker-compose.yml:19-20`
- `src/main/resources/schema.sql`
- `src/main/resources/data.sql`

---

## 🟢 Низкие проблемы

### 9. Swagger UI не документирует поле tokenType в AuthenticationResponse

**Файл:** `src/main/java/ru/tigran/personafeedbackengine/dto/AuthenticationResponse.java`
**Приоритет:** 🟢 НИЗКИЙ

**Проблема:**
В `AuthenticationResponse` добавлено поле `tokenType` с дефолтным значением "Bearer", но это не задокументировано в Swagger-аннотациях.

**Последствия:**
- Клиент API может не знать о наличии этого поля
- Не очевидно, что поле всегда содержит "Bearer"

**Рекомендация:**
Добавить аннотацию `@Schema` из Swagger:
```java
@Schema(description = "Token type, always 'Bearer'", example = "Bearer")
@JsonProperty("token_type")
String tokenType
```

**Затронутые файлы:**
- `src/main/java/ru/tigran/personafeedbackengine/dto/AuthenticationResponse.java:15-16`

---

### 10. SessionStatusInfo не задокументирован в dto/CLAUDE.md

**Файл:** `src/main/java/ru/tigran/personafeedbackengine/dto/CLAUDE.md`
**Приоритет:** 🟢 НИЗКИЙ

**Проблема:**
В проекте существует DTO `SessionStatusInfo`, который используется в `FeedbackResultRepository`, но он не упомянут в `dto/CLAUDE.md`.

**Последствия:**
- Неполная картина всех DTO в проекте
- Не очевидно назначение класса

**Рекомендация:**
Добавить описание в `dto/CLAUDE.md`:
```markdown
### SessionStatusInfo
- DTO для подсчета статусов в feedback session
- Fields:
  - `completedCount: Long` - Количество завершенных результатов
  - `failedCount: Long` - Количество провалившихся результатов
  - `totalCount: Long` - Общее количество результатов
- Usage: Internal DTO for FeedbackResultRepository.getSessionStatus()
```

**Затронутые файлы:**
- `src/main/java/ru/tigran/personafeedbackengine/dto/CLAUDE.md`

---

### 11. Отсутствует описание RetriableHttpException в exception/CLAUDE.md

**Файл:** `src/main/java/ru/tigran/personafeedbackengine/exception/CLAUDE.md`
**Приоритет:** 🟢 НИЗКИЙ

**Проблема:**
В списке Java файлов найден класс `RetriableHttpException.java`, но он не описан в `exception/CLAUDE.md`.

**Последствия:**
- Неполная документация исключений
- Неясно, когда и как используется это исключение

**Рекомендация:**
Добавить описание в `exception/CLAUDE.md`:
```markdown
### RetriableHttpException
- Thrown when HTTP request fails with retriable error (429, 502, 503, 504)
- Extends ApplicationException
- Used by AIGatewayService for retry logic
- HTTP status: 500 Internal Server Error (to client)
```

**Затронутые файлы:**
- `src/main/java/ru/tigran/personafeedbackengine/exception/CLAUDE.md`

---

## Рекомендации по приоритетам исправления

### Перед началом тестирования (обязательно):

1. **Проблема #1** - Исправить schema.sql или убрать из docker-compose.yml
2. **Проблема #2** - Добавить коды ошибок в ErrorCode enum
3. **Проблема #3** - Обновить repository/CLAUDE.md
4. **Проблема #8** - Решить конфликт Flyway vs schema.sql

### Средний приоритет (желательно до production):

5. **Проблема #6** - Улучшить безопасность JWT secret key
6. **Проблема #7** - Переименовать миграцию V1

### Низкий приоритет (можно отложить):

7. **Проблемы #4, #5, #9, #10, #11** - Улучшение документации

---

## Проверенные аспекты (без замечаний)

✅ **Конфигурация**
- pom.xml корректен, все зависимости на месте
- application.properties, application-docker.properties, application-prod.properties настроены правильно
- docker-compose.yml корректен (кроме проблемы #8)
- .env.example содержит все необходимые переменные
- .gitignore правильно исключает .env файл

✅ **Безопасность**
- JWT аутентификация настроена корректно
- BCrypt используется для хеширования паролей
- SecurityConfig правильно настраивает публичные и защищенные endpoints
- Swagger UI настроен с поддержкой JWT Bearer токенов

✅ **Структура CLAUDE.md**
- Все основные директории имеют CLAUDE.md файлы (кроме util/)
- Большинство документации актуально и подробно

✅ **Архитектура**
- Модульная структура проекта
- Правильное разделение ответственности (SRP)
- Использование Flyway для миграций
- Настроена работа с RabbitMQ, Redis, PostgreSQL

---

## Итоговая оценка готовности к тестированию

**Статус:** ⚠️ **Условно готов с оговорками**

**Критические проблемы (3)** должны быть исправлены перед началом полноценного тестирования, чтобы избежать:
- Ошибок запуска приложения
- Некорректной работы БД
- Путаницы в API кодах ошибок

**Средние проблемы (5)** не блокируют тестирование, но требуют внимания перед production релизом.

**Низкие проблемы (3)** - улучшения документации, которые можно отложить.

---

**Конец отчета**
