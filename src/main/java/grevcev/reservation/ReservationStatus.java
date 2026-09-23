package grevcev.reservation;

public enum ReservationStatus {
    PENDING,
    APPROVED,
    CANCELLED;

    public boolean canTransitionTo(ReservationStatus target){
        return switch (this){
            case PENDING -> target == APPROVED || target == CANCELLED;
            case APPROVED -> target == CANCELLED;
            case CANCELLED -> false;
        };
    }
    public static boolean isApproverRequired(ReservationStatus from, ReservationStatus to) {
        return to == APPROVED;   // APPROVED — только для админа
    }
}
