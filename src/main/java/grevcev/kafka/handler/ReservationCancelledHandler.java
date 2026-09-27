package grevcev.kafka.handler;

import grevcev.kafka.event.KafkaEventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReservationCancelledHandler {
    private final static Logger log = LoggerFactory.getLogger(ReservationCancelledHandler.class);

    public void handle(KafkaEventEnvelope eventEnvelope){
        log.info("Reservation created, eventId={}, payload={}",
                eventEnvelope.eventId(),
                eventEnvelope.payload());
    }
}
