package grevcev.kafka.service;

import grevcev.kafka.handler.ReservationApprovedHandler;
import grevcev.kafka.handler.ReservationCancelledHandler;
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
    private final ReservationApprovedHandler approvedHandler;
    private final ReservationCancelledHandler cancelledHandler;

    public KafkaEventProcessingService(ProcessedEventRepository repository,
                                       ReservationCreatedHandler createdHandler,
                                       ReservationApprovedHandler reservationApprovedHandler,
                                       ReservationCancelledHandler cancelledHandler) {
        this.repository = repository;
        this.createdHandler = createdHandler;
        this.approvedHandler = reservationApprovedHandler;
        this.cancelledHandler = cancelledHandler;

    }

    @Transactional
    public void processEvent(KafkaEventEnvelope envelope) {
        switch (envelope.eventType()) {
            case RESERVATION_CREATED -> {
                if (tryRegisterEvent(envelope)) return;
                createdHandler.handle(envelope);
            }
            case RESERVATION_APPROVED -> {
                if (tryRegisterEvent(envelope)) return;
                approvedHandler.handle(envelope);
            }
            case RESERVATION_CANCELLED -> {
                if (tryRegisterEvent(envelope)) return;
                cancelledHandler.handle(envelope);
            }
            default -> {
                log.error("Unsupported Kafka event type: {}", envelope.eventType());
                throw new IllegalStateException();
            }
        }
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
