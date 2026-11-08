package grevcev.outbox.model;

public enum OutboxEventStatus {
    NEW,
    PROCESSING,
    FAILED,
    PROCESSED
}
