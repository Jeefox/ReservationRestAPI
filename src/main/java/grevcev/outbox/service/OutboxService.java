package grevcev.outbox.service;

import grevcev.kafka.event.ReservationCreatedKafkaEvent;
import grevcev.kafka.event.KafkaEventEnvelope;
import grevcev.kafka.event.KafkaEventType;
import grevcev.outbox.model.OutboxEvent;
import grevcev.outbox.model.OutboxEventStatus;
import grevcev.outbox.repository.OutboxEventRepository;
import grevcev.reservation.model.Reservation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxService {

    private final ObjectMapper mapper;
    private final OutboxEventRepository repository;

    public OutboxService(ObjectMapper mapper,
                         OutboxEventRepository repository) {
        this.mapper = mapper;
        this.repository = repository;
    }

    public void createReservationCreatedEvent(Reservation reservation) {
        ReservationCreatedKafkaEvent event = new ReservationCreatedKafkaEvent(
                reservation.getId(),
                reservation.getUser().getId(),
                reservation.getRoom().getId(),
                reservation.getStartDate(),
                reservation.getEndDate()
        );
        UUID eventId = UUID.randomUUID();
        JsonNode payload = mapper.valueToTree(event);

        KafkaEventEnvelope envelope = new KafkaEventEnvelope(
                eventId,
                KafkaEventType.RESERVATION_CREATED,
                payload
        );

        JsonNode jsonNode = mapper.valueToTree(envelope);

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .id(eventId)
                .createdAt(LocalDateTime.now())
                .payload(jsonNode)
                .aggregateType("Reservation")
                .aggregateId(reservation.getId())
                .eventType(KafkaEventType.RESERVATION_CREATED.name())
                .status(OutboxEventStatus.NEW)
                .partitionKey(reservation.getRoom().getId().toString())
                .build();

        repository.save(outboxEvent);
    }

    @Transactional
    public List<OutboxEvent> claimBatch() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        List<OutboxEvent> events = repository.claimBatch(threshold);
        LocalDateTime now = LocalDateTime.now();
        events.stream()
                .forEach(event -> {
                    event.setStatus(OutboxEventStatus.PROCESSING);
                    event.setProcessingStartedAt(now);
                });
        return events;
    }

    @Transactional
    public void markAsProcessed(UUID eventId) {
        OutboxEvent event = repository.findById(eventId)
                .orElseThrow();

        event.setStatus(OutboxEventStatus.PROCESSED);
        event.setProcessedAt(LocalDateTime.now());

    }
}
