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
            SELECT *
            FROM outbox_events 
            WHERE status = 'NEW' 
            OR (status = 'PROCESSING'
            AND processing_started_at < :threshold)  
            ORDER BY created_at 
            LIMIT 10 
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> claimBatch(@Param("threshold")LocalDateTime threshold);
}