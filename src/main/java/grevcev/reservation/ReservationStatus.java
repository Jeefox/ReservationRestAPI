package grevcev.reservation;

public enum ReservationStatus {
    PENDING,
    APPROVED,
    CANCELLED,
    DELETED;

    public boolean canTransitionTo(ReservationStatus target){
        if (this == DELETED) {
            return false;
        }
        return switch (this){
            case PENDING -> target == APPROVED || target == CANCELLED || target == DELETED;
            case APPROVED -> target == CANCELLED || target == DELETED;
            case CANCELLED -> target == DELETED;
            case DELETED -> false;
        };
    }
    public static boolean isApproverRequired(ReservationStatus from, ReservationStatus to) {
        return to == APPROVED;   // APPROVED — только для админа
    }
}
