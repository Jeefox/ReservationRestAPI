package grevcev.kafka.service;

import grevcev.kafka.handler.ReservationCreatedHandler;
import grevcev.kafka.event.KafkaEventEnvelope;
import grevcev.kafka.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class KafkaEventProcessingService {
    private final ProcessedEventRepository repository;
    private final static Logger log = LoggerFactory.getLogger(KafkaEventProcessingService.class);
    private final ReservationCreatedHandler createdHandler;
    public KafkaEventProcessingService(ProcessedEventRepository repository, ReservationCreatedHandler createdHandler) {
        this.repository = repository;
        this.createdHandler = createdHandler;
    }

    @Transactional
    public void processEvent(KafkaEventEnvelope envelope) {
        if (envelope == null || envelope.eventId() == null || envelope.eventType() == null || envelope.payload() == null) {
            throw new IllegalArgumentException("Incomplete Kafka event envelope");
        }
        if (tryRegisterEvent(envelope)) return;
        createdHandler.handle(envelope);
    }

    private boolean tryRegisterEvent(KafkaEventEnvelope envelope) {
        int result = repository.insertProcessedEvent(envelope.eventId(), LocalDateTime.now());
        if (result == 0) {
            log.info("Event {} already processed", envelope.eventId());
            return true;
        }
        return false;
    }
}
