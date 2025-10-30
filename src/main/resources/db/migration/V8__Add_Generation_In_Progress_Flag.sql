-- Add generation_in_progress flag to prevent concurrent updates from multiple RabbitMQ consumers
-- This prevents optimistic locking conflicts when processing persona generation tasks asynchronously

ALTER TABLE personas ADD COLUMN IF NOT EXISTS generation_in_progress BOOLEAN NOT NULL DEFAULT FALSE;
