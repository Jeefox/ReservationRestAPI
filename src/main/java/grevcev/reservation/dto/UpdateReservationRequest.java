package grevcev.reservation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record UpdateReservationRequest(
        @NotNull @Positive Long roomId,
        @NotNull @FutureOrPresent LocalDate startDate,
        @NotNull LocalDate endDate) {
    @AssertTrue
    public boolean isDateValid(){
        if(startDate == null || endDate == null) return true;
        return endDate.isAfter(startDate);
    }
}


