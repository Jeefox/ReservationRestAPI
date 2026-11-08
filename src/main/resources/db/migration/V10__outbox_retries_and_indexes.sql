ALTER TABLE outbox_events ADD COLUMN attempts integer NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN last_error varchar(2000);
ALTER TABLE outbox_events ADD COLUMN next_attempt_at timestamp;
CREATE INDEX outbox_ready_idx ON outbox_events (created_at, id) WHERE status IN ('NEW', 'PROCESSING');
CREATE INDEX outbox_unfinished_key_idx ON outbox_events (partition_key, created_at, id) WHERE status <> 'PROCESSED';
CREATE INDEX outbox_processed_at_idx ON outbox_events (processed_at) WHERE status = 'PROCESSED';
CREATE INDEX processed_events_processed_at_idx ON processed_events (processed_at);

CREATE VIEW outbox_queue_metrics AS
SELECT count(*) FILTER (WHERE status IN ('NEW', 'PROCESSING')) AS backlog,
       count(*) FILTER (WHERE status = 'FAILED') AS failed,
       coalesce(sum(attempts) FILTER (WHERE last_error IS NOT NULL), 0) AS failed_attempts,
       coalesce(extract(epoch FROM (localtimestamp - min(created_at)
           FILTER (WHERE status IN ('NEW', 'PROCESSING', 'FAILED')))), 0) AS oldest_age_seconds
FROM outbox_events;
