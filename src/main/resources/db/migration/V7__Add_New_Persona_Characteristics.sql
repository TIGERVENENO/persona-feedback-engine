-- V7: Add new persona characteristics fields for enhanced demographic and psychographic data
-- These fields support the new persona generation request structure with:
-- - Country and city (instead of generic location/region)
-- - Min/max age range (instead of single age)
-- - Activity sphere and profession (industry classification)
-- - Income, interests, and additional parameters (psychographics)
-- - Characteristics hash for fast search and reuse

-- Add new characteristic fields to personas table
ALTER TABLE personas
ADD COLUMN country VARCHAR(2),                           -- ISO 3166-1 alpha-2 code (e.g., RU, US, GB)
ADD COLUMN city VARCHAR(100),                            -- City name (e.g., Moscow, New York)
ADD COLUMN min_age INTEGER,                              -- Minimum age from request
ADD COLUMN max_age INTEGER,                              -- Maximum age from request
ADD COLUMN activity_sphere VARCHAR(50),                  -- Activity sphere/industry (IT, FINANCE, HEALTHCARE, etc.)
ADD COLUMN profession VARCHAR(150),                      -- Specific profession/role (e.g., Senior Software Engineer)
ADD COLUMN income VARCHAR(100),                          -- Income range/level (e.g., $50k-$75k)
ADD COLUMN interests JSONB,                              -- JSON array of interests/hobbies
ADD COLUMN additional_params TEXT,                       -- Additional custom parameters (up to 500 chars)
ADD COLUMN characteristics_hash JSONB;                   -- JSON hash of all characteristics for caching and reuse

-- Add column comments
COMMENT ON COLUMN personas.country IS 'ISO 3166-1 alpha-2 country code (e.g., RU, US, GB)';
COMMENT ON COLUMN personas.city IS 'City name (e.g., Moscow, New York)';
COMMENT ON COLUMN personas.min_age IS 'Minimum age from persona generation request';
COMMENT ON COLUMN personas.max_age IS 'Maximum age from persona generation request';
COMMENT ON COLUMN personas.activity_sphere IS 'Activity sphere/industry classification (IT, FINANCE, HEALTHCARE, etc.)';
COMMENT ON COLUMN personas.profession IS 'Specific profession or role (e.g., Senior Software Engineer)';
COMMENT ON COLUMN personas.income IS 'Income range or level (e.g., $50k-$75k, Middle class)';
COMMENT ON COLUMN personas.interests IS 'JSON array of persona interests and hobbies';
COMMENT ON COLUMN personas.additional_params IS 'Additional custom parameters (max 500 chars)';
COMMENT ON COLUMN personas.characteristics_hash IS 'JSON hash of all characteristics for fast search, filtering, and persona reuse';

-- Update the index that filters by characteristics
-- Drop old characteristics-related index if it exists
DROP INDEX IF EXISTS idx_persona_characteristics;

-- Create new composite index for fast characteristic search
CREATE INDEX idx_persona_characteristics
ON personas(country, city, demographic_gender, activity_sphere, deleted)
WHERE deleted = false;

-- Create hash index for characteristics_hash lookup (for fast persona reuse search)
CREATE INDEX idx_persona_characteristics_hash
ON personas USING hash(characteristics_hash)
WHERE deleted = false;
