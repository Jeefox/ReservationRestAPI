package grevcev.outbox.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class OutboxEvent{
    @Id
    @Column
    private UUID id;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode payload;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    @Enumerated(EnumType.STRING)
    private OutboxEventStatus status;
    private LocalDateTime processingStartedAt;
}
