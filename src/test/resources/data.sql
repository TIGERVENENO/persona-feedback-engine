-- ============================================================================
-- PERSONA FEEDBACK ENGINE - Test Data Initialization (H2 Compatible)
-- ============================================================================
-- This file is automatically loaded for tests using the test profile.
-- It uses H2-compatible SQL syntax instead of PostgreSQL-specific syntax.
--
-- Configuration in application-test.properties:
--   Uses H2 in-memory database
--   Spring JPA ddl-auto: create-drop
--
-- Execution order:
--   1. schema.sql is created automatically by Hibernate
--   2. data.sql is executed (inserts test data)
--
-- ============================================================================

-- ============================================================================
-- TEST USER
-- ============================================================================
INSERT INTO users (id, username, email)
VALUES (1, 'testuser', 'testuser@example.com');

-- ============================================================================
-- TEST PRODUCTS
-- ============================================================================
-- Two sample products for testing feedback generation.
-- Includes detailed descriptions for realistic AI feedback.
INSERT INTO products (id, name, description, user_id)
VALUES
  (
    1,
    'Premium Coffee Maker',
    'A state-of-the-art coffee maker with programmable brewing and built-in grinder. Features WiFi connectivity and mobile app control. Can brew up to 12 cups, maintains optimal temperature for 2 hours, and includes voice control integration with major smart home platforms.',
    1
  ),
  (
    2,
    'Noise-Canceling Headphones',
    'Professional-grade wireless headphones with active noise cancellation, 30-hour battery life, and premium sound quality. Features include adaptive noise cancellation, spatial audio support, and seamless switching between 5 paired devices.',
    1
  );

-- ============================================================================
-- TEST PERSONA (Pre-Generated)
-- ============================================================================
-- A pre-generated persona in ACTIVE status for immediate testing.
-- This persona can be used right away without waiting for AI generation.
-- Use this persona ID (1) in feedback session requests to see results immediately.
INSERT INTO personas (
  id,
  name,
  detailed_description,
  gender,
  age_group,
  race,
  avatar_url,
  status,
  generation_prompt,
  user_id
)
VALUES (
  1,
  'Sarah Miller',
  'Sarah is a busy marketing manager in her early 30s who values convenience and quality in her daily tech purchases. She is tech-savvy and always looking for products that streamline her workflow. She appreciates premium features but is also budget-conscious. Sarah is an early adopter who follows tech trends closely and loves to share her experiences on social media. She works in fast-paced tech startups and needs tools that keep up with her lifestyle.',
  'Female',
  '28-35',
  'Caucasian',
  'https://example.com/avatars/sarah.jpg',
  'ACTIVE',
  'A tech-savvy marketing manager in her early 30s who values convenience and quality',
  1
);

-- ============================================================================
