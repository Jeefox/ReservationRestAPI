package grevcev.outbox.service;

import grevcev.kafka.producer.ReservationKafkaProducer;
import grevcev.outbox.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher {
    private final OutboxService outboxService;
    private final ReservationKafkaProducer kafkaProducer;

    @Scheduled(fixedDelay = 5000)
    public void publish() {
        List<OutboxEvent> events = outboxService.claimBatch();
        events.stream()
                .forEach(event -> {
                    kafkaProducer.send(event.getPartitionKey(), event.getPayload())
                            .whenComplete((result, ex) -> {
                                if (ex != null) {
                                    log.error(
                                            "Failed to publish outbox event {}",
                                            event.getId(),
                                            ex
                                    );
                                    return;
                                }
                                outboxService.markAsProcessed(event.getId());
                            });
                });
    }
}
