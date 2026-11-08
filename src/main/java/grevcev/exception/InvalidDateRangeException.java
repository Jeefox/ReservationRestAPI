package grevcev.exception;

public class InvalidDateRangeException extends RuntimeException {
    public InvalidDateRangeException() {
        super("Граница from должна быть раньше to");
    }
}
