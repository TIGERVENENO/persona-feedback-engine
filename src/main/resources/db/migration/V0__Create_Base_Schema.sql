-- Начальная миграция: создание полной схемы со всеми колонками
-- Включает поддержку soft delete и аутентификации

-- ============================================================================
-- USERS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(60) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Индексы для USERS
CREATE INDEX IF NOT EXISTS idx_user_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_user_deleted ON users(deleted);

-- ============================================================================
-- PRODUCTS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    user_id BIGINT NOT NULL,
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_products_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Индексы для PRODUCTS
CREATE INDEX IF NOT EXISTS idx_products_user_id ON products(user_id);
CREATE INDEX IF NOT EXISTS idx_products_user_id_id ON products(user_id, id);
CREATE INDEX IF NOT EXISTS idx_product_user_deleted ON products(user_id, deleted);

-- ============================================================================
-- PERSONAS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS personas (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    detailed_description TEXT,
    gender VARCHAR(50),
    age_group VARCHAR(50),
    race VARCHAR(100),
    avatar_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'GENERATING',
    generation_prompt TEXT NOT NULL,
    user_id BIGINT NOT NULL,
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_personas_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_personas_status CHECK (status IN ('GENERATING', 'ACTIVE', 'FAILED'))
);

-- Индексы для PERSONAS
CREATE INDEX IF NOT EXISTS idx_personas_user_id ON personas(user_id);
CREATE INDEX IF NOT EXISTS idx_personas_user_id_id ON personas(user_id, id);
CREATE INDEX IF NOT EXISTS idx_persona_user_deleted ON personas(user_id, deleted);
CREATE INDEX IF NOT EXISTS idx_persona_status ON personas(status);

-- ============================================================================
-- FEEDBACK_SESSIONS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS feedback_sessions (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_feedback_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_feedback_sessions_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

-- Индексы для FEEDBACK_SESSIONS
CREATE INDEX IF NOT EXISTS idx_feedback_sessions_user_id ON feedback_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_feedback_sessions_user_id_id ON feedback_sessions(user_id, id);
CREATE INDEX IF NOT EXISTS idx_feedback_sessions_status ON feedback_sessions(status);
CREATE INDEX IF NOT EXISTS idx_feedback_sessions_user_status ON feedback_sessions(user_id, status);

-- ============================================================================
-- FEEDBACK_RESULTS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS feedback_results (
    id BIGSERIAL PRIMARY KEY,
    feedback_text TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    feedback_session_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    persona_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_feedback_results_session FOREIGN KEY (feedback_session_id) REFERENCES feedback_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_feedback_results_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    CONSTRAINT fk_feedback_results_persona FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE RESTRICT,
    CONSTRAINT chk_feedback_results_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    CONSTRAINT unq_feedback_results_pair UNIQUE(feedback_session_id, product_id, persona_id)
);

-- Индексы для FEEDBACK_RESULTS
CREATE INDEX IF NOT EXISTS idx_feedback_results_session_id ON feedback_results(feedback_session_id);
CREATE INDEX IF NOT EXISTS idx_feedback_results_product_id ON feedback_results(product_id);
CREATE INDEX IF NOT EXISTS idx_feedback_results_persona_id ON feedback_results(persona_id);
CREATE INDEX IF NOT EXISTS idx_feedback_results_status ON feedback_results(status);
CREATE INDEX IF NOT EXISTS idx_feedback_results_session_status ON feedback_results(feedback_session_id, status);
CREATE INDEX IF NOT EXISTS idx_feedback_results_session_product_persona ON feedback_results(feedback_session_id, product_id, persona_id);

-- ============================================================================
-- TEST DATA (для тестов)
-- ============================================================================

-- TEST USER
INSERT INTO users (id, email, password_hash, is_active, deleted, created_at, updated_at)
VALUES (1, 'testuser@example.com', '$2a$10$abcdefghijklmnopqrstuvwxyz', true, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- TEST PRODUCTS
INSERT INTO products (id, name, description, user_id, deleted, created_at, updated_at)
VALUES
  (1, 'Premium Coffee Maker', 'A state-of-the-art coffee maker with programmable brewing and built-in grinder', 1, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, 'Noise-Canceling Headphones', 'Professional-grade wireless headphones with active noise cancellation', 1, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- TEST PERSONA
INSERT INTO personas (id, name, detailed_description, gender, age_group, race, avatar_url, status, generation_prompt, user_id, deleted, created_at, updated_at)
VALUES (
  1,
  'Sarah Miller',
  'Sarah is a busy marketing manager in her early 30s who values convenience and quality in her daily tech purchases',
  'Female',
  '28-35',
  'Caucasian',
  'https://example.com/avatars/sarah.jpg',
  'ACTIVE',
  'A tech-savvy marketing manager in her early 30s who values convenience and quality',
  1,
  false,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
);
