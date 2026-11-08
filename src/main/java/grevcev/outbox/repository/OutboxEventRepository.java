package grevcev.outbox.repository;

import grevcev.outbox.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository  extends JpaRepository<OutboxEvent, UUID> {
    @Query(value = """
            SELECT e.* FROM outbox_events e
            WHERE ((e.status = 'NEW' AND (e.next_attempt_at IS NULL OR e.next_attempt_at <= :now))
                OR (e.status = 'PROCESSING' AND e.processing_started_at < :threshold))
              AND NOT EXISTS (
                SELECT 1 FROM outbox_events earlier
                WHERE earlier.partition_key = e.partition_key AND earlier.status <> 'PROCESSED'
                  AND (earlier.created_at, earlier.id) < (e.created_at, e.id))
            ORDER BY e.created_at, e.id
            LIMIT :batchSize
            FOR UPDATE OF e SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> claimBatch(@Param("threshold") LocalDateTime threshold,
                                @Param("now") LocalDateTime now,
                                @Param("batchSize") int batchSize);

    @Override
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<OutboxEvent> findById(UUID id);
}
