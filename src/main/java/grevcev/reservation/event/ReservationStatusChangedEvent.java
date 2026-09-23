package grevcev.reservation.event;

import grevcev.reservation.ReservationStatus;

public record ReservationStatusChangedEvent(
        Long reservationId, ReservationStatus from, ReservationStatus to
) {
}
