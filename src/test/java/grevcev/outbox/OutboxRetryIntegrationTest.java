package grevcev.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import grevcev.AbstractIntegrationTest;
import grevcev.outbox.model.OutboxEvent;
import grevcev.outbox.model.OutboxEventStatus;
import grevcev.outbox.repository.OutboxEventRepository;
import grevcev.outbox.service.OutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class OutboxRetryIntegrationTest extends AbstractIntegrationTest {
    @Autowired private OutboxService service;
    @Autowired private OutboxEventRepository events;
    @Autowired private ObjectMapper mapper;

    @Test
    void temporaryFailureRetriesAndStaleCallbackCannotCompleteNewAttempt() {
        OutboxEvent first = event("first", LocalDateTime.now().minusMinutes(5));
        assertThat(service.claimBatch()).extracting(OutboxEvent::getId).contains(first.getId());
        service.markAsFailed(first.getId(), 1, new RuntimeException("temporary"));
        assertThat(first.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        first.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        events.flush();
        service.claimBatch();
        assertThat(first.getAttempts()).isEqualTo(2);
        service.markAsProcessed(first.getId(), 1);
        assertThat(first.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        service.markAsProcessed(first.getId(), 2);
        assertThat(first.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
        assertThat(first.getProcessedAt()).isNotNull();
    }

    @Test
    void permanentFailureIsBoundedAndBlocksLaterEventsForSameKey() {
        OutboxEvent first = event("one", LocalDateTime.now().minusMinutes(5));
        OutboxEvent second = event(first.getPartitionKey(), LocalDateTime.now().minusMinutes(4));
        for (int attempt = 1; attempt <= 5; attempt++) {
            first.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
            events.flush();
            assertThat(service.claimBatch()).extracting(OutboxEvent::getId).contains(first.getId()).doesNotContain(second.getId());
            service.markAsFailed(first.getId(), attempt, new RuntimeException("permanent"));
        }
        assertThat(first.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(first.getLastError()).contains("permanent");
        events.flush();
        assertThat(service.claimBatch()).extracting(OutboxEvent::getId).doesNotContain(first.getId(), second.getId());
    }

    private OutboxEvent event(String key, LocalDateTime created) {
        return events.saveAndFlush(OutboxEvent.builder().id(UUID.randomUUID()).aggregateType("Reservation")
                .aggregateId(1L).eventType("RESERVATION_CREATED").partitionKey(key)
                .payload(mapper.createObjectNode()).status(OutboxEventStatus.NEW).createdAt(created).build());
    }
}
