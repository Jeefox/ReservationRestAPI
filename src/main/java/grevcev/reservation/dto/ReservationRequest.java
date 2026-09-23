package grevcev.reservation.dto;

import grevcev.reservation.ReservationStatus;

import java.time.LocalDate;

public record ReservationRequest(
        String customerName,
        LocalDate reservationDate,
        ReservationStatus status
) {
}
