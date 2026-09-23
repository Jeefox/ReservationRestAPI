package grevcev.kafka.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.concurrent.CompletableFuture;

@Service
public class ReservationKafkaProducer {

    private final KafkaTemplate<String, JsonNode> kafkaTemplate;
    private final static Logger log = LoggerFactory.getLogger(ReservationKafkaProducer.class);

    public ReservationKafkaProducer(KafkaTemplate<String, JsonNode> kafkaTemplate){
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, JsonNode>> send(
            String key, JsonNode node) {

        return kafkaTemplate.send(
                "reservations",
                key,
                node
        ).whenComplete(
                (result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send reservation to Kafka", ex);
                        return;
                    }
                    log.info(
                            "Event sent: topic={}, partition={}, offset={}",
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()
                    );
                });
    }
}