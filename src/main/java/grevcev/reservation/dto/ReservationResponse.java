package grevcev.reservation.dto;

import grevcev.reservation.ReservationStatus;

import java.time.LocalDate;

public record ReservationResponse(
        Long id,
        Long userId,
        String userName,
        Long roomId,
        String roomName,
        LocalDate startDate,
        LocalDate endDate,
        ReservationStatus status) {
}
