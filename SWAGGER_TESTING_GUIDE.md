# Swagger API Testing Guide

Это руководство описывает как тестировать Persona Feedback Engine API через Swagger UI.

## Доступ к Swagger UI

Когда приложение запущено на `http://localhost:8080`, Swagger UI доступен по адресу:

```
http://localhost:8080/swagger-ui.html
```

Или альтернативные URL:
- `http://localhost:8080/swagger-ui/` (директория)
- API документация: `http://localhost:8080/v3/api-docs`

## Структура API

API разделена на 3 основные группы:

### 1. **Authentication** (Регистрация и логин)
**Не требуют JWT токена**

#### POST /api/v1/auth/register
Создает новый аккаунт пользователя.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "SecurePassword123"
}
```

**Требования:**
- Email должен быть уникальным и валидным форматом
- Пароль минимум 8 символов, максимум 128

**Response (201 Created):**
```json
{
  "user_id": 1,
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer"
}
```

#### POST /api/v1/auth/login
Логин с email и паролем, получение JWT токена.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "SecurePassword123"
}
```

**Response (200 OK):**
```json
{
  "user_id": 1,
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer"
}
```

---

### 2. **Personas** (Генерация AI персон)
**Требуют JWT токена**

#### POST /api/v1/personas
Запускает асинхронную генерацию AI персоны по описанию.

**Как использовать JWT токен в Swagger:**
1. Откройте любой protected endpoint (например, эту операцию)
2. В правом верхнем углу найдите кнопку "🔒 Authorize"
3. В появившемся окне в поле "value" вставьте токен из ответа логина:
   ```
   eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
   ```
4. Нажмите "Authorize"
5. Теперь все защищенные запросы будут автоматически отправлять токен

**Request Body:**
```json
{
  "prompt": "A product manager focused on DevOps tools with 5+ years of experience in cloud infrastructure. Cares deeply about automation and scalability."
}
```

**Требования:**
- Prompt не должен быть пустым
- Максимум 2000 символов

**Response (202 Accepted):**
```json
{
  "id": 42,
  "status": "GENERATING"
}
```

**Примеры personas для тестирования:**

1. **Tech CTO:**
   ```
   Chief Technology Officer with 15+ years of experience building scalable systems.
   Experts in architecture, performance optimization, and team management.
   Focuses on ROI and technical debt reduction.
   ```

2. **Startup Founder:**
   ```
   Young startup founder, bootstrapped company, limited budget.
   Needs tools that grow with them. Values community and transparency.
   Needs to validate product-market fit quickly.
   ```

3. **Enterprise Admin:**
   ```
   IT Administrator in large corporation with 10000+ employees.
   Manages complex infrastructure, compliance requirements.
   Needs enterprise support and audit trails.
   ```

---

### 3. **Feedback Sessions** (Сбор фидбека от персон на продукты)
**Требуют JWT токена**

#### POST /api/v1/feedback-sessions
Запускает асинхронную генерацию фидбека от personas на products.

**Request Body:**
```json
{
  "productIds": [1, 2],
  "personaIds": [10, 20]
}
```

**Требования:**
- Минимум 1 product ID
- Минимум 1 persona ID
- Максимум 5 products
- Максимум 5 personas

**Response (202 Accepted):**
```json
{
  "id": 123,
  "status": "PENDING"
}
```

#### GET /api/v1/feedback-sessions/{sessionId}
Получить статус сессии и результаты фидбека.

**Path Parameters:**
- `sessionId` - ID сессии (число)

**Query Parameters (опциональны):**
- `page` - Номер страницы (0-based), пример: `0`
- `size` - Размер страницы, пример: `10`

**Response (200 OK) без pagination:**
```json
{
  "sessionId": 123,
  "status": "COMPLETED",
  "feedbackResults": [
    {
      "id": 1001,
      "productId": 1,
      "personaId": 10,
      "feedback": "Great product! I especially like...",
      "status": "COMPLETED"
    },
    {
      "id": 1002,
      "productId": 2,
      "personaId": 10,
      "feedback": "Good but needs...",
      "status": "COMPLETED"
    }
  ],
  "pagination": null
}
```

**Response (200 OK) с pagination:**
```json
{
  "sessionId": 123,
  "status": "COMPLETED",
  "feedbackResults": [
    {
      "id": 1001,
      "productId": 1,
      "personaId": 10,
      "feedback": "Great product!...",
      "status": "COMPLETED"
    }
  ],
  "pagination": {
    "pageNumber": 0,
    "pageSize": 10,
    "totalCount": 15
  }
}
```

---

## Полный workflow для тестирования

### Шаг 1: Регистрация нового пользователя
1. Откройте `POST /api/v1/auth/register`
2. Нажмите "Try it out"
3. Введите email и пароль:
   ```json
   {
     "email": "testuser@example.com",
     "password": "TestPass123"
   }
   ```
4. Нажмите "Execute"
5. Скопируйте значение `access_token` из ответа (без кавычек)

### Шаг 2: Авторизация в Swagger
1. Нажмите кнопку "🔒 Authorize" в правом верхнем углу
2. Вставьте скопированный токен в поле "value"
3. Нажмите "Authorize"

### Шаг 3: Создание personas
1. Откройте `POST /api/v1/personas`
2. Нажмите "Try it out"
3. Введите prompt:
   ```json
   {
     "prompt": "A technical founder building DevOps tools. Cares about automation and performance."
   }
   ```
4. Нажмите "Execute"
5. Запомните ID из ответа, например `"id": 42`

### Шаг 4: Создание feedback session
1. Откройте `POST /api/v1/feedback-sessions`
2. Нажмите "Try it out"
3. Введите IDs:
   ```json
   {
     "productIds": [1],
     "personaIds": [42]
   }
   ```
4. Нажмите "Execute"
5. Запомните ID сессии, например `"id": 123`

### Шаг 5: Проверка статуса фидбека
1. Откройте `GET /api/v1/feedback-sessions/{sessionId}`
2. Нажмите "Try it out"
3. Введите `123` в поле sessionId
4. Нажмите "Execute"
5. Фидбек будет доступен когда status станет "COMPLETED"

---

## Коды ошибок

| Status | Описание |
|--------|---------|
| 200 | OK - Успешный запрос |
| 201 | Created - Ресурс успешно создан |
| 202 | Accepted - Асинхронная задача принята |
| 400 | Bad Request - Ошибка в параметрах запроса |
| 401 | Unauthorized - Отсутствует или неверный JWT токен |
| 403 | Forbidden - Пользователь не имеет доступа к ресурсу |
| 404 | Not Found - Ресурс не найден |
| 500 | Internal Server Error - Ошибка сервера |

---

## Локальное тестирование без Swagger

Если предпочитаете использовать `curl`, вот примеры:

### Регистрация:
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePass123"
  }'
```

### Логин:
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePass123"
  }'
```

### Создание persona (замени TOKEN на реальный токен):
```bash
curl -X POST http://localhost:8080/api/v1/personas \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN" \
  -d '{
    "prompt": "A technical founder..."
  }'
```

---

## Troubleshooting

**Проблема:** "401 Unauthorized" на protected endpoints
- **Решение:** Убедитесь что вы нажали "Authorize" и вставили корректный токен

**Проблема:** Swagger UI не загружается
- **Решение:** Проверьте что приложение запущено на `http://localhost:8080`

**Проблема:** "Email already exists"
- **Решение:** Используйте другой email адрес для регистрации

**Проблема:** Personas/Products не найдены
- **Решение:** Сначала создайте personas через API перед использованием их в feedback sessions

---

## Полезные ссылки

- OpenAPI Specification: https://swagger.io/specification/
- Springdoc OpenAPI: https://springdoc.org/
- JWT Tokens: https://jwt.io/
