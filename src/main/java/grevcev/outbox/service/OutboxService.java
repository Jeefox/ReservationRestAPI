package grevcev.outbox.service;

import grevcev.kafka.event.ReservationCreatedKafkaEvent;
import grevcev.kafka.event.KafkaEventEnvelope;
import grevcev.kafka.event.KafkaEventType;
import grevcev.outbox.model.OutboxEvent;
import grevcev.outbox.model.OutboxEventStatus;
import grevcev.outbox.repository.OutboxEventRepository;
import grevcev.reservation.model.Reservation;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import java.util.ArrayList;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxService {

    @Value("${app.outbox.batch-size:10}")
    private int batchSize;
    @Value("${app.outbox.max-attempts:5}")
    private int maxAttempts;
    @Value("${app.outbox.lease:PT10M}")
    private Duration lease;
    @Value("${app.outbox.retry-delay:PT10S}")
    private Duration retryDelay;

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
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> events = repository.claimBatch(now.minus(lease), now, batchSize);
        List<OutboxEvent> claimed = new ArrayList<>();
        for (OutboxEvent event : events) {
            if (event.getAttempts() >= maxAttempts) {
                event.setStatus(OutboxEventStatus.FAILED);
                event.setLastError("Publication lease expired after final attempt");
                continue;
            }
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setProcessingStartedAt(now);
            event.setAttempts(event.getAttempts() + 1);
            claimed.add(event);
        }
        return claimed;
    }

    @Transactional
    public void markAsProcessed(UUID eventId, int attempt) {
        OutboxEvent event = repository.findById(eventId).orElseThrow();
        if (event.getStatus() != OutboxEventStatus.PROCESSING || event.getAttempts() != attempt) return;
        event.setStatus(OutboxEventStatus.PROCESSED);
        event.setProcessedAt(LocalDateTime.now());
        event.setLastError(null);
        event.setNextAttemptAt(null);
    }

    @Transactional
    public void markAsFailed(UUID eventId, int attempt, Throwable failure) {
        OutboxEvent event = repository.findById(eventId).orElseThrow();
        if (event.getStatus() != OutboxEventStatus.PROCESSING || event.getAttempts() != attempt) return;
        event.setStatus(attempt >= maxAttempts ? OutboxEventStatus.FAILED : OutboxEventStatus.NEW);
        String error = failure.toString();
        event.setLastError(error.substring(0, Math.min(error.length(), 2000)));
        event.setNextAttemptAt(LocalDateTime.now().plus(retryDelay));
    }
}
