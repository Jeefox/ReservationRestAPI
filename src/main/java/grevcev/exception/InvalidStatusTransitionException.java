package grevcev.exception;

import grevcev.reservation.ReservationStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(ReservationStatus oldStatus, ReservationStatus newStatus) {
        super("Cannot transition status from " + oldStatus + " to " + newStatus);
    }
}
