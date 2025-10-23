-- ============================================================================
-- PERSONA FEEDBACK ENGINE - Test Data Initialization
-- ============================================================================
-- This file is automatically loaded on application startup in Docker profile.
--
-- Configuration in application-docker.properties:
--   spring.sql.init.mode=always
--
-- Execution order:
--   1. schema.sql is executed first (creates all tables)
--   2. data.sql is executed second (inserts test data)
--
-- This test data is useful for:
--   - Local development and testing
--   - Demonstration of API workflows
--   - Integration testing
--
-- WARNING: This file uses "INSERT ... ON CONFLICT DO NOTHING" to avoid errors
-- if data already exists (e.g., after multiple restarts).
-- ============================================================================

-- ============================================================================
-- TEST USER
-- ============================================================================
-- Creates a test user for local development.
-- This user owns the test products and personas below.
INSERT INTO users (id, username, email)
VALUES (1, 'testuser', 'testuser@example.com')
ON CONFLICT DO NOTHING;

COMMENT ON COLUMN users.id IS 'Test user ID used as X-User-Id header in API calls';

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
  )
ON CONFLICT DO NOTHING;

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
)
ON CONFLICT DO NOTHING;

-- ============================================================================
-- EXAMPLE API USAGE
-- ============================================================================
-- Once the application is running, you can test with these IDs:
--
-- 1. Use pre-generated persona immediately:
--    POST /api/v1/feedback-sessions
--    Headers: X-User-Id: 1
--    Body: {
--      "productIds": [1, 2],
--      "personaIds": [1]
--    }
--
-- 2. Generate a new persona:
--    POST /api/v1/personas
--    Headers: X-User-Id: 1
--    Body: {
--      "prompt": "A 25-year-old software engineer who loves gaming and tech gadgets"
--    }
--    Note: Wait 3-5 seconds for AI generation before using in feedback session
--
-- 3. Check feedback session status:
--    GET /api/v1/feedback-sessions/{sessionId}
--    Headers: X-User-Id: 1
--    Note: Wait 3-5 seconds for feedback generation to complete
--
-- ============================================================================
