-- Миграция для добавления колонок аутентификации
-- Добавляет поддержку регистрации и логина через email и пароль

-- Добавить колону email (если ее еще нет) и установить как unique
-- Сначала добавляем временную колону с default значением, потом удаляем default
ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255);
UPDATE users SET email = CONCAT('user_', id, '@example.com') WHERE email IS NULL;
ALTER TABLE users ALTER COLUMN email SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE(email);

-- Добавить колону для хранения хеша пароля (BCrypt создает хеш из 60 символов)
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255) DEFAULT '';
UPDATE users SET password_hash = '' WHERE password_hash IS NULL;
ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash DROP DEFAULT;

-- Добавить колону для отслеживания активности аккаунта
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;
UPDATE users SET is_active = TRUE WHERE is_active IS NULL;
ALTER TABLE users ALTER COLUMN is_active SET NOT NULL;

-- Удалить старую колону username если она существует (заменена на email)
ALTER TABLE users DROP COLUMN IF EXISTS username;

-- Создать индекс для быстрого поиска по email
CREATE INDEX IF NOT EXISTS idx_user_email ON users(email);
