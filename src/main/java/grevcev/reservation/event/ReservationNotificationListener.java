package grevcev.reservation.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class ReservationNotificationListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ReservationCreatedEvent event){
        log.info("Reservation with id {} on room {} from {} to {} has been created",
                event.reservationId(), event.roomId(), event.startDate(), event.endDate());
    }

    @TransactionalEventListener(phase =  TransactionPhase.AFTER_COMMIT)
    void on(ReservationStatusChangedEvent event){
        log.info("Reservation with id {} status updated from {} to {}",
                event.reservationId(), event.from(), event.to());
    }
}
