package grevcev.reservation.event;

import java.time.LocalDate;

public record ReservationCreatedEvent(
        Long reservationId, Long roomId, LocalDate startDate, LocalDate endDate
) {
}
