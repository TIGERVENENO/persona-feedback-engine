-- Добавление поля валюты к продуктам
ALTER TABLE products
ADD COLUMN currency VARCHAR(3);

COMMENT ON COLUMN products.currency IS 'ISO 4217 валютный код (USD, RUB, EUR и т.д.)';
