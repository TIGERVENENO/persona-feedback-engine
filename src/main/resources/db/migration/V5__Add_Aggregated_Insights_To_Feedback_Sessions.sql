-- V5: Add aggregated_insights field to feedback_sessions for storing AI-aggregated feedback summary
-- This field stores averageScore, purchaseIntentPercent, and keyThemes extracted from all feedback results

-- Add aggregated insights field
ALTER TABLE feedback_sessions
ADD COLUMN aggregated_insights JSONB;

-- Add comment
COMMENT ON COLUMN feedback_sessions.aggregated_insights IS 'AI-aggregated insights: {averageScore, purchaseIntentPercent, keyThemes: [{theme, mentions}]}';

-- Create index for faster JSON queries on aggregated insights
CREATE INDEX idx_feedback_session_insights ON feedback_sessions USING gin(aggregated_insights);
