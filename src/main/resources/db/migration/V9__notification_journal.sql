-- Existing records are internal journal entries, not pending email deliveries.
UPDATE notification SET status = 'RECORDED' WHERE status IN ('PENDING', 'SENT');
ALTER TABLE notification ALTER COLUMN status SET DEFAULT 'RECORDED';
-- Keep the historical sent_at column for compatibility with existing data.
