CREATE TABLE notification (
    id UUID PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    event_id UUID NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,

    CONSTRAINT fk_notification_reservation
    FOREIGN KEY (reservation_id)
    REFERENCES reservations(id),

    CONSTRAINT fk_notification_user
    FOREIGN KEY (user_id)
    REFERENCES users(id)
);