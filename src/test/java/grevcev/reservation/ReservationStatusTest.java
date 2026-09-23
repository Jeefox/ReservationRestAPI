package grevcev.reservation;

import grevcev.reservation.ReservationStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReservationStatusTest {
    @Test
    void pendingCanGoToApprovedOrCancelled() {
        assertTrue(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.APPROVED));
        assertTrue(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.CANCELLED));
        assertFalse(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.PENDING));
    }

    @Test
    void approvedCanOnlyBeCancelled() {
        assertTrue(ReservationStatus.APPROVED.canTransitionTo(ReservationStatus.CANCELLED));
        assertFalse(ReservationStatus.APPROVED.canTransitionTo(ReservationStatus.PENDING));
    }

    @Test
    void cancelledIsTerminal() {
        assertFalse(ReservationStatus.CANCELLED.canTransitionTo(ReservationStatus.APPROVED));
        assertFalse(ReservationStatus.CANCELLED.canTransitionTo(ReservationStatus.PENDING));
        assertFalse(ReservationStatus.CANCELLED.canTransitionTo(ReservationStatus.CANCELLED));
    }
}
