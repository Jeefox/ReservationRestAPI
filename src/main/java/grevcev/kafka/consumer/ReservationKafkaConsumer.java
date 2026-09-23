package grevcev.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import grevcev.kafka.event.ReservationCreatedKafkaEvent;

@Component
public class ReservationKafkaConsumer {

    Logger log = LoggerFactory.getLogger(ReservationKafkaConsumer.class);

    @KafkaListener(
            topics = "reservations",
            groupId = "reservation-test-2",
            concurrency = "2"
    )
    public void consume(
            ConsumerRecord<String, ReservationCreatedKafkaEvent> record
    ) {
        log.info(
                "Consumer thread={}, partition={}, offset={}, key={}, value={}",
                Thread.currentThread().getName(),
                record.partition(),
                record.offset(),
                record.key(),
                record.value()
        );
    }
}
