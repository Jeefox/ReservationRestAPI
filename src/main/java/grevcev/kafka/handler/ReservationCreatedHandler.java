package grevcev.kafka.handler;

import grevcev.kafka.event.KafkaEventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReservationCreatedHandler {
    public static final Logger log = LoggerFactory.getLogger(ReservationCreatedHandler.class);

    public void handle(KafkaEventEnvelope envelope){
        log.info(
                "Reservation created, eventId={}, payload={}",
                envelope.eventId(),
                envelope.payload()
        );
    }
}
