-- V4: Add demographics fields to personas table for target audience matching
-- These fields enable finding/reusing personas based on demographic parameters

-- Add demographic fields
ALTER TABLE personas
ADD COLUMN gender VARCHAR(10),
ADD COLUMN age INTEGER,
ADD COLUMN region VARCHAR(50),
ADD COLUMN income_level VARCHAR(20),
ADD COLUMN demographics_hash JSONB;

-- Add comments
COMMENT ON COLUMN personas.gender IS 'Gender: male, female, other';
COMMENT ON COLUMN personas.age IS 'Exact age of persona (e.g., 27, 34)';
COMMENT ON COLUMN personas.region IS 'Region: moscow, spb, regions';
COMMENT ON COLUMN personas.income_level IS 'Income level: low, medium, high';
COMMENT ON COLUMN personas.demographics_hash IS 'JSON hash of demographics for uniqueness and caching';

-- Create composite index for fast demographic search
CREATE INDEX idx_persona_demographics ON personas(gender, age, region, income_level) WHERE deleted = false;

-- Create hash index for demographics_hash lookup
CREATE INDEX idx_persona_demographics_hash ON personas USING hash(demographics_hash) WHERE deleted = false;
