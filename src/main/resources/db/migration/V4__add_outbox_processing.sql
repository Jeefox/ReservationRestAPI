ALTER TABLE outbox_events
ADD COLUMN status varchar(20) NOT NULL DEFAULT 'NEW',
ADD COLUMN processing_started_at timestamp;