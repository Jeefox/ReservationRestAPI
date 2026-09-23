package grevcev.reservation.dto;

import jakarta.validation.constraints.NotNull;
import grevcev.reservation.ReservationStatus;
import grevcev.validation.ValidEnum;

public record UpdateStatusRequest(
        @NotNull(message = "Status cannot be null")
        @ValidEnum(enumClass = ReservationStatus.class, message = "Invalid status value")
        ReservationStatus status) {
}
