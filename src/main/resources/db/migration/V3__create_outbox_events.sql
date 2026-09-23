create table outbox_events (
    id uuid primary key,
    aggregate_type varchar(100) not null,
    aggregate_id bigint not null,
    event_type varchar(100) not null,
    payload jsonb not null,
    created_at timestamp not null,
    processed_at timestamp
);