package grevcev.outbox.service;

import grevcev.kafka.producer.ReservationKafkaProducer;
import grevcev.outbox.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher {
    private final OutboxService outboxService;
    private final ReservationKafkaProducer kafkaProducer;

    @Scheduled(fixedDelayString = "${app.outbox.publish-delay-ms:5000}")
    public void publish() {
        for (OutboxEvent event : outboxService.claimBatch()) {
            try {
                kafkaProducer.send(event.getPartitionKey(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                outboxService.markAsProcessed(event.getId(), event.getAttempts());
                            } else {
                                log.error("Failed to publish outbox event {}", event.getId(), ex);
                                outboxService.markAsFailed(event.getId(), event.getAttempts(), ex);
                            }
                        });
            } catch (RuntimeException ex) {
                log.error("Failed to submit outbox event {}", event.getId(), ex);
                outboxService.markAsFailed(event.getId(), event.getAttempts(), ex);
            }
        }
    }
}
