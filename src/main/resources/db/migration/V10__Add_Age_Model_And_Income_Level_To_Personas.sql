-- V10__Add_Age_Model_And_Income_Level_To_Personas.sql
-- Adds age, model, and income_level fields to personas table
-- Removes duplicate deprecated income_level String field
-- Consolidates income handling using IncomeLevel enum

-- 1. Add new columns
ALTER TABLE personas ADD COLUMN age INTEGER;
COMMENT ON COLUMN personas.age IS 'Exact age generated for this persona';

ALTER TABLE personas ADD COLUMN model VARCHAR(50);
COMMENT ON COLUMN personas.model IS 'AI model used for generation (e.g., claude-3-5-sonnet, gpt-4o)';

-- 2. Rename existing income column to income_level (enum type)
-- First create new enum type for income level
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'income_level_enum') THEN
        CREATE TYPE income_level_enum AS ENUM ('low', 'medium', 'high');
    END IF;
END
$$;

-- 2. Drop old string column if it exists and create new enum column
ALTER TABLE personas DROP COLUMN IF EXISTS income_level;

ALTER TABLE personas ADD COLUMN income_level income_level_enum;
COMMENT ON COLUMN personas.income_level IS 'Income classification (LOW, MEDIUM, HIGH)';

-- 3. Drop old income column (string) if it exists
-- Note: This assumes income column was a string for income range like "$50k-$75k"
ALTER TABLE personas DROP COLUMN IF EXISTS income;

-- 4. Populate region column from city for backward compatibility
-- If region is NULL but city exists, copy city value to region
UPDATE personas SET region = city WHERE region IS NULL AND city IS NOT NULL;

-- Add index for better search performance on age
CREATE INDEX IF NOT EXISTS idx_persona_age ON personas(age);
