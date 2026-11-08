ALTER TABLE reservations ADD COLUMN version bigint NOT NULL DEFAULT 0;

-- NOT VALID preserves legacy rows; PostgreSQL enforces the constraints on new writes.
ALTER TABLE reservations ADD CONSTRAINT reservation_dates_order CHECK (end_date > start_date) NOT VALID;
ALTER TABLE rooms ADD CONSTRAINT room_positive_capacity CHECK (capacity > 0) NOT VALID;

CREATE INDEX reservation_active_room_dates_idx ON reservations (room_id, end_date, start_date)
    WHERE status IN ('PENDING', 'APPROVED');
