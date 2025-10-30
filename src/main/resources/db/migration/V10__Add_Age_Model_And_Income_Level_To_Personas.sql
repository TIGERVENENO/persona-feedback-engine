-- V10__Add_Age_Model_And_Income_Level_To_Personas.sql
-- Adds model field and converts income handling to IncomeLevel enum
-- Note: age column already exists from V7, region already exists

-- 1. Add model column if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_NAME='personas' AND COLUMN_NAME='model') THEN
        ALTER TABLE personas ADD COLUMN model VARCHAR(50) NULL;
        COMMENT ON COLUMN personas.model IS 'AI model used for generation (e.g., claude-3-5-sonnet, gpt-4o)';
    END IF;
END
$$;

-- 2. Create enum type for income level if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'income_level_enum') THEN
        CREATE TYPE income_level_enum AS ENUM ('low', 'medium', 'high');
    END IF;
END
$$;

-- 3. Drop old deprecated string income_level column if it exists
-- Keep the new income_level enum column
DO $$
BEGIN
    -- Check if the old string income_level column exists
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_NAME='personas' AND COLUMN_NAME='income_level'
               AND DATA_TYPE='character varying') THEN
        ALTER TABLE personas DROP COLUMN income_level;
    END IF;
END
$$;

-- 4. Add new income_level enum column if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_NAME='personas' AND COLUMN_NAME='income_level') THEN
        ALTER TABLE personas ADD COLUMN income_level income_level_enum NULL;
        COMMENT ON COLUMN personas.income_level IS 'Income classification (LOW, MEDIUM, HIGH)';
    END IF;
END
$$;

-- 5. Drop old income column (string) if it exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_NAME='personas' AND COLUMN_NAME='income') THEN
        ALTER TABLE personas DROP COLUMN income;
    END IF;
END
$$;

-- 6. Populate region column from city for backward compatibility (if needed)
-- If region is NULL but city exists, copy city value to region
UPDATE personas SET region = city WHERE region IS NULL AND city IS NOT NULL;

-- 7. Add index for better search performance on age if it doesn't exist
CREATE INDEX IF NOT EXISTS idx_persona_age ON personas(age);
