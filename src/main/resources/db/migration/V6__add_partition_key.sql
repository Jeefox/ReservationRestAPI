ALTER TABLE outbox_events
ADD COLUMN partition_key varchar(255);