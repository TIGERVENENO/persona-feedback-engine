-- Добавление поля language в таблицу feedback_sessions
-- Хранит двухбуквенный код языка по стандарту ISO 639-1 (EN, RU, FR и т.д.)

ALTER TABLE feedback_sessions
ADD COLUMN language VARCHAR(2) NOT NULL DEFAULT 'EN';

-- Комментарий для колонки
COMMENT ON COLUMN feedback_sessions.language IS 'ISO 639-1 двухбуквенный код языка для генерации фидбека (EN, RU, FR и т.д.)';
