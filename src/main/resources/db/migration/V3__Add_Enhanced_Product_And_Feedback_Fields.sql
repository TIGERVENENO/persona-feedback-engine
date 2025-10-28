-- Добавление расширенных полей для продуктов и фидбека
-- Поддержка структурированных промптов для более детальной генерации

-- ============================================================================
-- PRODUCTS TABLE - добавление полей для структурированного промпта
-- ============================================================================
ALTER TABLE products
ADD COLUMN price DECIMAL(10, 2),
ADD COLUMN category VARCHAR(100),
ADD COLUMN key_features JSONB;

COMMENT ON COLUMN products.price IS 'Цена продукта';
COMMENT ON COLUMN products.category IS 'Категория продукта';
COMMENT ON COLUMN products.key_features IS 'JSON массив ключевых характеристик продукта';

-- ============================================================================
-- PERSONAS TABLE - добавление product_attitudes для детального профиля
-- ============================================================================
ALTER TABLE personas
ADD COLUMN product_attitudes TEXT;

COMMENT ON COLUMN personas.product_attitudes IS 'Описание того, как персона обычно оценивает продукты в категории (на английском)';

-- ============================================================================
-- FEEDBACK_RESULTS TABLE - добавление структурированного выхода
-- ============================================================================
ALTER TABLE feedback_results
ADD COLUMN purchase_intent INTEGER,
ADD COLUMN key_concerns JSONB;

COMMENT ON COLUMN feedback_results.purchase_intent IS 'Намерение покупки по шкале 1-10';
COMMENT ON COLUMN feedback_results.key_concerns IS 'JSON массив основных опасений персоны';

-- Добавляем проверку диапазона для purchase_intent
ALTER TABLE feedback_results
ADD CONSTRAINT chk_purchase_intent_range CHECK (purchase_intent IS NULL OR (purchase_intent >= 1 AND purchase_intent <= 10));
