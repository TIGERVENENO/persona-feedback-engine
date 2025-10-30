-- V11__Remove_Unused_Persona_Fields.sql
-- Удаляет неиспользуемые поля из таблицы personas
-- gender, age_group, race больше не заполняются AI (их нет в промте)
-- demographics_hash заменен на characteristics_hash

-- 1. Drop gender column if it exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_NAME='personas' AND COLUMN_NAME='gender') THEN
        ALTER TABLE personas DROP COLUMN gender;
        RAISE NOTICE 'Dropped column gender from personas table';
    END IF;
END
$$;

-- 2. Drop age_group column if it exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_NAME='personas' AND COLUMN_NAME='age_group') THEN
        ALTER TABLE personas DROP COLUMN age_group;
        RAISE NOTICE 'Dropped column age_group from personas table';
    END IF;
END
$$;

-- 3. Drop race column if it exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_NAME='personas' AND COLUMN_NAME='race') THEN
        ALTER TABLE personas DROP COLUMN race;
        RAISE NOTICE 'Dropped column race from personas table';
    END IF;
END
$$;

-- 4. Drop demographics_hash column if it exists (deprecated, replaced by characteristics_hash)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_NAME='personas' AND COLUMN_NAME='demographics_hash') THEN
        ALTER TABLE personas DROP COLUMN demographics_hash;
        RAISE NOTICE 'Dropped column demographics_hash from personas table';
    END IF;
END
$$;
