-- Insert test user
INSERT INTO users (username, email) VALUES ('testuser', 'testuser@example.com');

-- Insert test products
INSERT INTO products (name, description, user_id) VALUES
  ('Premium Coffee Maker', 'A state-of-the-art coffee maker with programmable brewing and built-in grinder. Features WiFi connectivity and mobile app control.', 1),
  ('Noise-Canceling Headphones', 'Professional-grade wireless headphones with active noise cancellation, 30-hour battery life, and premium sound quality.', 1);

-- Insert test persona (pre-generated with ACTIVE status)
INSERT INTO personas (
  name,
  detailed_description,
  gender,
  age_group,
  race,
  avatar_url,
  status,
  generation_prompt,
  user_id
) VALUES (
  'Sarah Miller',
  'Sarah is a busy marketing manager who values convenience and quality in her daily tech purchases. She is tech-savvy, always looking for products that streamline her workflow. She appreciates premium features but is also budget-conscious. Sarah is an early adopter who follows tech trends and loves to share her experiences on social media.',
  'Female',
  '28-35',
  'Caucasian',
  'https://example.com/avatars/sarah.jpg',
  'ACTIVE',
  'A tech-savvy marketing manager in her early 30s who values convenience and quality',
  1
);
