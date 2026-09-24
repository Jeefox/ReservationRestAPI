package grevcev.kafka.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record KafkaEventEnvelope (
        UUID eventId,
        KafkaEventType eventType,
        JsonNode payload
){
}

