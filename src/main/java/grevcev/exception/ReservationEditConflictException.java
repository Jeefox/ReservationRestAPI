package grevcev.exception;

public class ReservationEditConflictException extends RuntimeException {
    public ReservationEditConflictException(String message) {
        super(message);
    }
}
