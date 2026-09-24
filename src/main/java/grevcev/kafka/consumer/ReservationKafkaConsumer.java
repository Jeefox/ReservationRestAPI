package grevcev.kafka.consumer;

import grevcev.kafka.event.KafkaEventEnvelope;
import grevcev.kafka.service.KafkaEventProcessingService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReservationKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReservationKafkaConsumer.class);
    private final KafkaEventProcessingService service;

    public ReservationKafkaConsumer(KafkaEventProcessingService service){
        this.service = service;
    }

    @KafkaListener(
            topics = "reservations",
            groupId = "reservation-test-2",
            concurrency = "2"
    )
    public void consume(
            ConsumerRecord<String, KafkaEventEnvelope> record
    ) {
        KafkaEventEnvelope envelope = record.value();
        log.info(
                "eventId={}, eventType={}, partition={}, offset={}, key={}",
                envelope.eventId(),
                envelope.eventType(),
                record.partition(),
                record.offset(),
                record.key()
        );
        service.processEvent(envelope);
    }
}
