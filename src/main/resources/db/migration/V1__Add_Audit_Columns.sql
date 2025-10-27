-- Миграция V1: Добавление аудит колонок для AuditableEntity
-- Добавляет created_by, updated_by и version колонки во все таблицы

-- ============================================================================
-- USERS TABLE
-- ============================================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) NOT NULL DEFAULT 'system';
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) NOT NULL DEFAULT 'system';
ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- ============================================================================
-- PRODUCTS TABLE
-- ============================================================================
ALTER TABLE products ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) NOT NULL DEFAULT 'system';
ALTER TABLE products ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) NOT NULL DEFAULT 'system';
ALTER TABLE products ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- ============================================================================
-- PERSONAS TABLE
-- ============================================================================
ALTER TABLE personas ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) NOT NULL DEFAULT 'system';
ALTER TABLE personas ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) NOT NULL DEFAULT 'system';
ALTER TABLE personas ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- ============================================================================
-- FEEDBACK_SESSIONS TABLE
-- ============================================================================
ALTER TABLE feedback_sessions ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) NOT NULL DEFAULT 'system';
ALTER TABLE feedback_sessions ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) NOT NULL DEFAULT 'system';
ALTER TABLE feedback_sessions ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- ============================================================================
-- FEEDBACK_RESULTS TABLE
-- ============================================================================
ALTER TABLE feedback_results ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) NOT NULL DEFAULT 'system';
ALTER TABLE feedback_results ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) NOT NULL DEFAULT 'system';
ALTER TABLE feedback_results ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
