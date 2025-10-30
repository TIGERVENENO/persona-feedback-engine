-- V10__Add_Age_Model_And_Income_Level_To_Personas.sql
-- Adds model and income_level (as VARCHAR) fields to personas table
-- Note: age column already exists from V7, region already exists
-- IncomeLevel is stored as VARCHAR (low, medium, high), not as PostgreSQL enum type

-- 1. Add model column if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_NAME='personas' AND COLUMN_NAME='model') THEN
        ALTER TABLE personas ADD COLUMN model VARCHAR(50);
        COMMENT ON COLUMN personas.model IS 'AI model used for generation (e.g., claude-3-5-sonnet, gpt-4o)';
    END IF;
END
$$;

-- 2. Add income_level column as VARCHAR (not PostgreSQL enum)
-- This stores income level as string: low, medium, high
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_NAME='personas' AND COLUMN_NAME='income_level') THEN
        ALTER TABLE personas ADD COLUMN income_level VARCHAR(10);
        COMMENT ON COLUMN personas.income_level IS 'Income classification (low, medium, high)';
    END IF;
END
$$;

-- 3. Drop old income column (old string field) if it exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_NAME='personas' AND COLUMN_NAME='income') THEN
        ALTER TABLE personas DROP COLUMN income;
    END IF;
END
$$;

-- 4. Populate region column from city for backward compatibility (if needed)
-- If region is NULL but city exists, copy city value to region
UPDATE personas SET region = city WHERE region IS NULL AND city IS NOT NULL;

-- 5. Add index for better search performance on age if it doesn't exist
CREATE INDEX IF NOT EXISTS idx_persona_age ON personas(age);
