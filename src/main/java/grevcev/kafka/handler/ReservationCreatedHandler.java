package grevcev.kafka.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import grevcev.kafka.event.KafkaEventEnvelope;
import grevcev.kafka.event.ReservationCreatedKafkaEvent;
import grevcev.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReservationCreatedHandler {
    public static final Logger log = LoggerFactory.getLogger(ReservationCreatedHandler.class);
    private final NotificationService notificationService;
    private final  ObjectMapper objectMapper;

    public ReservationCreatedHandler(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    public void handle(KafkaEventEnvelope envelope){
        try{
            ReservationCreatedKafkaEvent event =
                    objectMapper.treeToValue(
                            envelope.payload(),
                            ReservationCreatedKafkaEvent.class
                    );
            notificationService.createReservationCreatedNotification(envelope.eventId(), event);
            log.info(
                    "Reservation created, eventId={}, payload={}",
                    envelope.eventId(),
                    envelope.payload()
            );
        } catch (JsonProcessingException exception){
            throw new IllegalStateException(
                    "Failed to deserialize ReservationCreated event, eventId="
                            + envelope.eventId(),
                    exception
            );
        }
    }
}
