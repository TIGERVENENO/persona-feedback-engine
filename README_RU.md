# Persona Feedback Engine MVP

Надежный и масштабируемый backend сервис на Spring Boot 3.5.7 для генерации AI-персон и симуляции отзывов о продуктах. Построен как модульный монолит с асинхронной обработкой через RabbitMQ.

## Обзор

Persona Feedback Engine позволяет маркетологам:
1. **Генерировать AI-персоны** - Создавать детальные, реалистичные профили клиентов с помощью любой LLM модели
2. **Симулировать отзывы** - Получать отзывы о продуктах от нескольких персон одновременно
3. **Управлять сессиями** - Отслеживать прогресс генерации и получать результаты
4. **Выбирать провайдера и модель** - Переключаться между провайдерами (OpenRouter, AgentRouter) и моделями без изменений кода

Система использует двухэтапный асинхронный workflow:
- **Этап 1**: Тяжелая генерация персон (кэшируется по промпту)
- **Этап 2**: Легкая генерация отзывов с использованием сгенерированных персон

Вся обработка разделена через RabbitMQ, чтобы API оставался отзывчивым.

## Быстрый старт

Запустите весь проект за 3 минуты:

```bash
# 1. Клонируйте репозиторий
git clone <repository-url>
cd persona-feedback-engine

# 2. Создайте конфигурацию из шаблона
cp .env.example .env

# 3. Отредактируйте .env и установите API ключ выбранного провайдера
#    AGENTROUTER_API_KEY=your_key_here (для AgentRouter)
#    или OPENROUTER_API_KEY=your_key_here (для OpenRouter)

# 4. Запустите инфраструктуру (PostgreSQL, Redis, RabbitMQ)
docker-compose up -d

# 5. Соберите Spring Boot приложение
mvn clean package

# 6. Запустите приложение (в другом терминале)
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=docker"

# 7. Приложение готово по адресу http://localhost:8080
```

**Проверьте, что сервисы запущены:**
```bash
docker-compose ps
# Должны отображаться: postgres, redis, rabbitmq - все со статусом "healthy"
```

## Требования

### Обязательные

- **Java 21** - [Скачать JDK 21](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
- **Maven 3.8+** - [Скачать Maven](https://maven.apache.org/download.cgi)
- **Docker Desktop** - [Скачать Docker](https://www.docker.com/products/docker-desktop/)
- **API ключ провайдера** - Выберите один из:
  - **OpenRouter** - [Получить бесплатные кредиты](https://openrouter.ai/keys)
  - **AgentRouter** - [Получить бесплатные кредиты](https://agentrouter.ai/keys)

### Опциональные

- **PostgreSQL Client** (psql) - для прямого доступа к базе данных
- **Redis CLI** - для управления Redis
- **curl** или **Postman** - для тестирования API

## Инструкции по установке

### Шаг 1: Конфигурация окружения

```bash
# Скопируйте файл примера переменных окружения
cp .env.example .env

# Отредактируйте .env с вашими учетными данными
# Самое важное: установите API_PROVIDER и соответствующий ключ
nano .env  # или ваш предпочитаемый редактор
```

**Ключевые переменные окружения:**
- `AI_PROVIDER` - Какой провайдер использовать: `openrouter` или `agentrouter` (по умолчанию: `agentrouter`)
- `OPENROUTER_API_KEY` - Ваш OpenRouter API ключ (обязателен если используете OpenRouter)
- `AGENTROUTER_API_KEY` - Ваш AgentRouter API ключ (обязателен если используете AgentRouter)
- `OPENROUTER_MODEL` - Модель для OpenRouter (по умолчанию: deepseek/deepseek-r1-0528:free)
- `AGENTROUTER_MODEL` - Модель для AgentRouter (по умолчанию: deepseek/deepseek-v3.1)
- `POSTGRES_PASSWORD` - Пароль базы данных (по умолчанию: postgres)
- `REDIS_PASSWORD` - Пароль Redis (по умолчанию: redispass)
- `RABBITMQ_PASSWORD` - Пароль RabbitMQ (по умолчанию: guest)

### Шаг 2: Запуск Docker сервисов

```bash
# Запустите все сервисы в фоне
docker-compose up -d

# Проверьте, что все сервисы запущены и здоровы
docker-compose ps

# Просмотрите логи (полезно для отладки)
docker-compose logs -f

# Остановите сервисы (без удаления данных)
docker-compose stop

# Удалите контейнеры и volumes (ПРЕДУПРЕЖДЕНИЕ: удалит все данные)
docker-compose down -v
```

**Ожидаемый вывод команды `docker-compose ps`:**
```
NAME                        STATUS      PORTS
persona-feedback-postgres   healthy     0.0.0.0:5432->5432/tcp
persona-feedback-redis      healthy     0.0.0.0:6379->6379/tcp
persona-feedback-rabbitmq   healthy     0.0.0.0:5672->5672/tcp, 0.0.0.0:15672->15672/tcp
```

### Шаг 3: Сборка Spring Boot приложения

```bash
# Чистая сборка и упаковка приложения
mvn clean package

# Создает файл target/persona-feedback-engine-0.0.1-SNAPSHOT.jar
# Также запускает все unit и интеграционные тесты

# Пропустить тесты при необходимости (не рекомендуется)
mvn clean package -DskipTests
```

### Шаг 4: Запуск приложения

```bash
# Запустите с включенным Docker профилем
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=docker"

# Или запустите JAR напрямую
java -Dspring.profiles.active=docker -jar target/persona-feedback-engine-0.0.1-SNAPSHOT.jar

# Приложение запустится на http://localhost:8080
```

**Вы должны увидеть логи вроде:**
```
Started PersonaFeedbackEngineApplication in 5.234 seconds
Tomcat initialized with port(s): 8080 (http)
HikariPool-1 - Starting - connections: 10
```

### Шаг 5: Проверка, что приложение работает

```bash
# Простая проверка здоровья
curl http://localhost:8080/actuator/health

# Ожидаемый ответ:
# {"status":"UP"}
```

## Гайд по конфигурации провайдера и модели

Система поддерживает несколько провайдеров и моделей ИИ. Вы можете переключаться между ними без изменения кода.

### Поддерживаемые провайдеры

| Провайдер | Формат ключа | URL | Описание |
|----------|---|---|---|
| **OpenRouter** | `sk-or-...` | https://openrouter.ai/api/v1/chat/completions | Доступ ко многим моделям, оплата по использованию |
| **AgentRouter** | `sk-or-v1-...` | https://api.agentrouter.ai/v1/chat/completions | Оптимизированная маршрутизация, конкурентные цены |

### Доступные модели

Оба провайдера поддерживают множество LLM моделей.
Смотрите [OpenRouter Models](https://openrouter.ai/docs#models) или [AgentRouter Models](https://agentrouter.ai/docs#models) для полного списка.

### Как переключаться между провайдерами и моделями

#### Вариант 1: Переменные окружения (Рекомендуется)

Отредактируйте файл `.env`:

```bash
# Выберите провайдера
AI_PROVIDER=agentrouter

# Установите модель для каждого провайдера
OPENROUTER_MODEL=deepseek/deepseek-r1-0528:free
AGENTROUTER_MODEL=deepseek/deepseek-v3.1

# Установите учетные данные
AGENTROUTER_API_KEY=sk-or-v1-your-agentrouter-key-here
OPENROUTER_API_KEY=sk-or-your-openrouter-key-here  # опционально если не используете OpenRouter
```

Затем перезагрузите приложение.

#### Вариант 2: Прямое редактирование файла конфигурации

Отредактируйте `src/main/resources/application.properties`:

```properties
# ======== Конфигурация AI провайдера ========
app.ai.provider=agentrouter

# ======== Конфигурация OpenRouter ========
app.openrouter.api-key=sk-or-YOUR_OPENROUTER_API_KEY_HERE
app.openrouter.model=deepseek/deepseek-r1-0528:free
app.openrouter.retry-delay-ms=1000

# ======== Конфигурация AgentRouter ========
app.agentrouter.api-key=sk-or-v1-YOUR_AGENTROUTER_API_KEY_HERE
app.agentrouter.model=deepseek/deepseek-v3.1
app.agentrouter.retry-delay-ms=1000
```

### Сравнение провайдеров

**Когда использовать OpenRouter:**
- Нужен доступ к большому разнообразию моделей
- Хотите сравнивать разные провайдеры ИИ
- Разрабатываете провайдер-агностичные приложения

**Когда использовать AgentRouter:**
- Нужна оптимизированная маршрутизация и балансировка нагрузки
- Нужна стабильная производительность
- Предпочитаете конкурентные цены

### Совместимость API

Оба провайдера используют одинаковый OpenAI-совместимый формат API, поэтому переключение безопасно:

```json
{
  "model": "<ВАШ_ВЫБРАННЫЙ_MODEL>",
  "messages": [
    {
      "role": "system",
      "content": "Ваш системный промпт"
    },
    {
      "role": "user",
      "content": "Ваше сообщение"
    }
  ]
}
```

Вы можете менять это в конфигурации в любой момент.

`AIGatewayService` автоматически обрабатывает детали провайдера (URLs, аутентификация, retry логика, выбор модели) на основе вашей конфигурации.

---

## API Endpoints

### 1. Генерировать AI персону

**Endpoint:** `POST /api/v1/personas`

**Описание:** Запускает асинхронную генерацию персоны. Возвращает немедленно с ID задачи.

**Headers:**
```
X-User-Id: 1
Content-Type: application/json
```

**Тело запроса:**
```json
{
  "prompt": "Создай техно-продвинутого маркетолога-миллениала, который ценит инновации и дизайн"
}
```

**Пример с curl:**
```bash
curl -X POST http://localhost:8080/api/v1/personas \
  -H "X-User-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Маркетолог в конце 20-х, любит стартапы и кофе"
  }'
```

**Ответ (HTTP 202 - Accepted):**
```json
{
  "jobId": 1,
  "status": "GENERATING"
}
```

**Что происходит дальше:**
- Запись персоны создается со статусом GENERATING
- Задача публикуется в очередь RabbitMQ
- Consumer обрабатывает задачу асинхронно
- AI генерирует детальную персону (имя, пол, возрастная группа, интересы)
- Статус персоны обновляется на ACTIVE при завершении
- Персона кэшируется по промпту для переиспользования в будущих сессиях

---

### 2. Создать сессию отзывов

**Endpoint:** `POST /api/v1/feedback-sessions`

**Описание:** Создает сессию отзывов и генерирует отзывы от указанных персон на указанные продукты.

**Headers:**
```
X-User-Id: 1
Content-Type: application/json
```

**Тело запроса:**
```json
{
  "productIds": [1, 2],
  "personaIds": [1]
}
```

**Пример с curl:**
```bash
curl -X POST http://localhost:8080/api/v1/feedback-sessions \
  -H "X-User-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "productIds": [1, 2],
    "personaIds": [1]
  }'
```

**Ответ (HTTP 202 - Accepted):**
```json
{
  "jobId": 1,
  "status": "PENDING"
}
```

**Что происходит дальше:**
- FeedbackSession создается со статусом PENDING
- FeedbackResult записи создаются для каждой пары (продукт, персона)
  - В этом примере: 2 продукта × 1 персона = 2 результата отзыва
- Задачи публикуются в RabbitMQ для асинхронной обработки
- Consumers получают детали каждой персоны и продукта
- AI генерирует текст отзыва с точки зрения каждой персоны
- FeedbackResults обновляются с текстом отзыва и статус меняется на COMPLETED
- FeedbackSession статус обновляется на COMPLETED когда все результаты готовы

---

### 3. Опросить статус сессии отзывов

**Endpoint:** `GET /api/v1/feedback-sessions/{sessionId}`

**Описание:** Получает текущий статус и все результаты отзывов для сессии.

**Headers:**
```
X-User-Id: 1
```

**Пример с curl:**
```bash
curl -X GET http://localhost:8080/api/v1/feedback-sessions/1 \
  -H "X-User-Id: 1"
```

**Ответ (HTTP 200):**
```json
{
  "id": 1,
  "status": "COMPLETED",
  "createdAt": "2024-10-24T10:30:00",
  "feedbackResults": [
    {
      "id": 1,
      "feedbackText": "Эта кофемашина потрясающая! Возможность WiFi позволяет мне планировать варку с телефона...",
      "status": "COMPLETED",
      "personaId": 1,
      "productId": 1
    },
    {
      "id": 2,
      "feedbackText": "Отличный дизайн, но хотелось бы более сильной шумоизоляции в шумных офисах...",
      "status": "COMPLETED",
      "personaId": 1,
      "productId": 2
    }
  ]
}
```

---

### 4. Полный пример workflow

```bash
# Шаг 1: Генерируем персону
PERSONA_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/personas \
  -H "X-User-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "32-летний инженер, который любит гаджеты и автоматизацию"
  }')

PERSONA_ID=$(echo $PERSONA_RESPONSE | grep -o '"jobId":[0-9]*' | cut -d':' -f2)
echo "Сгенерирована персона с ID: $PERSONA_ID"

# Подождите немного для завершения генерации (обычно < 5 секунд)
sleep 3

# Шаг 2: Создаем сессию отзывов с использованием сгенерированной персоны
SESSION_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/feedback-sessions \
  -H "X-User-Id: 1" \
  -H "Content-Type: application/json" \
  -d "{
    \"productIds\": [1],
    \"personaIds\": [$PERSONA_ID]
  }")

SESSION_ID=$(echo $SESSION_RESPONSE | grep -o '"jobId":[0-9]*' | cut -d':' -f2)
echo "Создана сессия отзывов с ID: $SESSION_ID"

# Подождите завершения генерации отзывов (обычно < 10 секунд)
sleep 5

# Шаг 3: Опросите результаты
curl -X GET http://localhost:8080/api/v1/feedback-sessions/$SESSION_ID \
  -H "X-User-Id: 1" | jq .
```

## Доступ к интерфейсам управления сервисами

### UI управления RabbitMQ

Получите доступ к консоли управления RabbitMQ для мониторинга очередей сообщений:

```
URL: http://localhost:15672
Пользователь: guest
Пароль: guest (из .env)
```

**Полезные вьюшки:**
- **Queues**: `persona.generation.queue` и `feedback.generation.queue`
- **Connections**: Показывает активные подключения приложения
- **Messages**: Отображает глубину очередей и количество сообщений

### База данных PostgreSQL

Подключитесь напрямую к базе данных:

```bash
# Используя psql (если установлен)
psql -h localhost -U postgres -d personadb -W

# Введите пароль при запросе (из .env)

# Полезные запросы:
# Список всех пользователей
SELECT * FROM users;

# Список всех персон и их статус
SELECT id, name, status, user_id FROM personas;

# Список результатов сессии отзывов
SELECT * FROM feedback_results WHERE status = 'COMPLETED';

# Проверить глубину очередей сообщений
SELECT COUNT(*) as queue_depth FROM feedback_results WHERE status = 'PENDING';
```

### Redis Cache

Подключитесь к Redis:

```bash
# Используя redis-cli (если установлен)
redis-cli -p 6379 -a redispass

# Просмотрите кэшированные персоны
KEYS persona*

# Получите кэшированную персону (пример)
GET "personaCache::Ваш промпт генерации персоны здесь"

# Мониторьте команды в реальном времени
MONITOR
```

## Тестирование приложения

### Запуск тестов

```bash
# Запустить все тесты
mvn test

# Запустить только интеграционные тесты
mvn verify

# Запустить определенный тестовый класс
mvn test -Dtest=FeedbackServiceIntegrationTest

# Запустить с отчетом покрытия
mvn clean test jacoco:report
# Просмотрите отчет: target/site/jacoco/index.html
```

### Покрытие интеграционными тестами

Приложение включает комплексные интеграционные тесты:

- ✅ FeedbackService создает правильные отношения между сущностями
- ✅ Несколько персон в одной сессии
- ✅ Валидация максимального количества продуктов/персон
- ✅ Проверка владельца пользователя
- ✅ Поведение каскадного удаления
- ✅ Логика завершения сессии

## Обзор архитектуры

### Диаграмма компонентов

```
┌─────────────────────────────────────────────────────────────┐
│                      REST API Layer                         │
│  POST /api/v1/personas     POST /api/v1/feedback-sessions   │
│  GET /api/v1/feedback-sessions/{id}                        │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                   Service Layer                             │
│  PersonaService          FeedbackService      AIGatewayService
│  - Валидация             - Создание сессии    - OpenRouter API
│  - Публикация задач      - Публикация задач   - Оптимизация токенов
└──────────────────┬──────────────────┬────────────────────────┘
                   │                  │
        ┌──────────▼──────────┐      └──────┐
        │                     │             │
        │   RabbitMQ Queues   │             │
        │                     │             │
        │ persona.generation  │ feedback    │
        │ .queue              │ .generation │
        │                     │ .queue      │
        └──────────┬──────────┘             │
                   │                       │
        ┌──────────▼──────────┐   ┌────────▼──────────┐
        │  PersonaTaskConsumer│   │FeedbackTaskConsumer
        │                     │   │
        │ AI → Обновить      │   │ AI → Обновить
        │   Персону          │   │   Результат
        └────────┬────────────┘   └────────┬──────────┘
                 │                         │
                 └────────────┬────────────┘
                              │
        ┌─────────────────────▼─────────────────────┐
        │       Spring Data JPA + PostgreSQL        │
        │  Users, Products, Personas, Sessions,    │
        │  FeedbackResults                         │
        └─────────────────────┬─────────────────────┘
                              │
        ┌─────────────────────▼─────────────────────┐
        │  Caching (Redis) + Persistence            │
        │  24-часовой кэш персон по промпту       │
        └──────────────────────────────────────────┘
```

### Поток данных

1. **Генерация персоны**
   - API получает промпт → PersonaService валидирует → Создает Persona (GENERATING)
   - Задача публикуется в `persona.generation.queue`
   - PersonaTaskConsumer обрабатывает → Вызывает AIGatewayService → Обновляет Persona (ACTIVE)
   - AI ответ кэшируется по промпту для переиспользования

2. **Генерация отзывов**
   - API получает продукты + персоны → FeedbackService валидирует владение
   - Создает FeedbackSession (PENDING) + FeedbackResults (PENDING)
   - Задача публикуется в `feedback.generation.queue` для каждой пары продукт-персона
   - FeedbackTaskConsumer обрабатывает → Получает кэшированную персону + продукт → Вызывает AI
   - Обновляет FeedbackResult (COMPLETED)
   - Когда все результаты готовы → Обновляет FeedbackSession (COMPLETED)

## Файлы конфигурации

### Ключевые файлы конфигурации

| Файл | Назначение |
|------|-----------|
| `application.properties` | Конфигурация по умолчанию (localhost) |
| `application-docker.properties` | Docker конфигурация (использует имена сервисов) |
| `.env.example` | Шаблон переменных окружения |
| `docker-compose.yml` | Оркестрация инфраструктуры |
| `schema.sql` | Database DDL (auto-loaded на старте) |
| `data.sql` | Тестовые данные (auto-loaded на старте) |

### Профили окружения

- **Локальная разработка**: `mvn spring-boot:run` (использует application.properties)
- **Docker**: `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=docker"` (использует application-docker.properties)
- **Тестирование**: Автоматически использует H2 in-memory базу с application-test.properties

## Решение проблем

### Сервисы не запускаются

**Проблема:** `docker-compose up` fails with connection errors

**Решение:**
```bash
# Проверьте логи сервиса
docker-compose logs postgres
docker-compose logs redis
docker-compose logs rabbitmq

# Проверьте доступность портов
# Порты 5432 (PostgreSQL), 6379 (Redis), 5672/15672 (RabbitMQ)
netstat -an | grep -E "(5432|6379|5672|15672)"

# Если порты заняты, либо:
# 1. Остановите другие сервисы, использующие эти порты
# 2. Измените порты в docker-compose.yml
```

### Приложение не может подключиться к базе данных

**Проблема:** `org.postgresql.util.PSQLException: Connection to postgres:5432 refused`

**Решение:**
```bash
# 1. Проверьте, что PostgreSQL запущен
docker-compose ps | grep postgres

# 2. Проверьте логи базы данных
docker-compose logs postgres

# 3. Проверьте, что .env имеет правильный POSTGRES_PASSWORD
cat .env | grep POSTGRES

# 4. Протестируйте подключение напрямую
psql -h localhost -U postgres -d personadb -W
```

### OpenRouter API ключ не работает

**Проблема:** `AIGatewayException: Failed to call OpenRouter API`

**Решение:**
```bash
# 1. Проверьте, что .env имеет OPENROUTER_API_KEY установленный
grep OPENROUTER_API_KEY .env

# 2. Протестируйте API ключ напрямую (замените на ваш ключ)
curl https://openrouter.ai/api/v1/models \
  -H "Authorization: Bearer sk-or-YOUR_KEY_HERE"

# 3. Проверьте OpenRouter dashboard для API квоты
# Посетите https://openrouter.ai/account

# 4. Убедитесь, что ключ не истек и не был отозван
```

### Тесты не проходят

**Проблема:** Интеграционные тесты fail с ошибками базы данных

**Решение:**
```bash
# Очистите H2 кэш базы данных
rm -rf ~/.h2.server

# Запустите тесты с чистым Maven
mvn clean test

# Запустите определенный тест с debug логированием
mvn test -Dtest=FeedbackServiceIntegrationTest -X

# Или в IDE, правый клик на тестовый класс и "Run with Debug"
```

## Development Workflow

### Добавление новой функции

1. Создайте feature branch
2. Напишите тесты первыми (TDD)
3. Реализуйте функцию
4. Запустите интеграционные тесты: `mvn verify`
5. Закоммитьте с сообщением на русском: `git commit -m "Описание изменений"`
6. Push и создайте pull request

### Отладка в IDE

**IntelliJ IDEA / Eclipse:**
1. Установите breakpoint в коде
2. Запустите: `mvn spring-boot:run` с включенной отладкой
3. IDE debugger подключится автоматически

**Пример с Maven:**
```bash
mvn -Dspring-boot.run.arguments="--spring.profiles.active=docker" spring-boot:run -DskipTests
```

## Соображения для production

### Перед развертыванием на production:

1. **Безопасность**
   - ✓ Используйте сильные, уникальные пароли (20+ символов)
   - ✓ Храните секреты в vault/secrets manager, не в .env
   - ✓ Включите SSL/TLS для всех сервисов
   - ✓ Реализуйте JWT аутентификацию (не X-User-Id mock)
   - ✓ Добавьте rate limiting запросов
   - ✓ Валидируйте все входные данные строго

2. **База данных**
   - ✓ Включите автоматизированные backups
   - ✓ Сконфигурируйте replication для high availability
   - ✓ Настройте monitoring и alerting
   - ✓ Регулярный VACUUM и ANALYZE

3. **Message Queue**
   - ✓ Включите persistence (AOF для Redis, default queues для RabbitMQ)
   - ✓ Настройте dead-letter queues для failed сообщений
   - ✓ Реализуйте message TTL стратегии

4. **Monitoring**
   - ✓ Log aggregation (ELK, Splunk, etc.)
   - ✓ Metrics collection (Prometheus, Grafana)
   - ✓ Distributed tracing (Jaeger, DataDog)
   - ✓ APM для performance monitoring

5. **Scaling**
   - ✓ Horizontal scaling с load balancer
   - ✓ Database connection pooling tuning
   - ✓ Cache warming стратегии
   - ✓ Async worker scaling

## Контрибьютинг

Контрибьюции приветствуются! Пожалуйста:

1. Форкните репозиторий
2. Создайте feature branch: `git checkout -b feature/amazing-feature`
3. Закоммитьте изменения: `git commit -m "описание изменений"`
4. Push в branch: `git push origin feature/amazing-feature`
5. Откройте Pull Request

## Лицензия

Этот проект лицензирован под MIT License - см. файл LICENSE для подробностей.

## Поддержка

Для вопросов, проблем или предложений:
- Откройте issue на GitHub
- Проверьте существующую документацию в `src/main/java/ru/tigran/personafeedbackengine/*/CLAUDE.md`
- Посмотрите code comments и architecture docs

## Changelog

### Version 0.0.1-SNAPSHOT (MVP)
- ✨ Инициальный MVP с генерацией персон и симуляцией отзывов
- ✨ RabbitMQ-based асинхронная обработка
- ✨ Redis кэширование для переиспользования персон
- ✨ REST API с асинхронным отслеживанием задач
- ✨ Комплексные интеграционные тесты
- ✨ Docker поддержка с docker-compose
