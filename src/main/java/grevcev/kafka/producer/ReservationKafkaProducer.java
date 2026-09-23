package grevcev.kafka.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import grevcev.kafka.event.ReservationCreatedKafkaEvent;

@Service
public class ReservationKafkaProducer {

    private final KafkaTemplate<String, ReservationCreatedKafkaEvent> kafkaTemplate;
    private final static Logger log = LoggerFactory.getLogger(ReservationKafkaProducer.class);

    public ReservationKafkaProducer(KafkaTemplate<String, ReservationCreatedKafkaEvent> kafkaTemplate){
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendReservationToKafka(ReservationCreatedKafkaEvent event){
        kafkaTemplate.send(
                "reservations",
                event.roomId().toString(),
                event).whenComplete((result, ex)->{
                    if (ex!=null){
                        log.error("Failed to send reservation to Kafka", ex);
                    }
                    log.info(
                            "Reservation sent: topic={}, patrition={}, offset={}",
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()
                    );
        });
    }
}