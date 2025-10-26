-- Миграция для soft delete поддержки
-- Добавляет колону deleted ко всем основным таблицам

ALTER TABLE users ADD COLUMN deleted BOOLEAN DEFAULT FALSE NOT NULL;
CREATE INDEX idx_user_deleted ON users(deleted);

ALTER TABLE products ADD COLUMN deleted BOOLEAN DEFAULT FALSE NOT NULL;
CREATE INDEX idx_product_user_deleted ON products(user_id, deleted);

ALTER TABLE personas ADD COLUMN deleted BOOLEAN DEFAULT FALSE NOT NULL;
CREATE INDEX idx_persona_user_deleted ON personas(user_id, deleted);
CREATE INDEX idx_persona_status ON personas(status);
