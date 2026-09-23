package grevcev.reservation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UpdateReservationRequest(
        @NotNull Long roomId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {
    @AssertTrue
    public boolean isDateValid(){
        if(startDate == null || endDate == null) return true;
        return !endDate.isBefore(startDate);
    }
}


