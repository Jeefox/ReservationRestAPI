package grevcev.exception;

import java.time.LocalDate;

public class RoomAlreadyBookedException extends RuntimeException {
    public RoomAlreadyBookedException (Long roomId, LocalDate startDate, LocalDate endDate) {
        super("Room " + roomId + " is already booked from " + startDate + " to " + endDate);
    }
}