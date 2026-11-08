package grevcev.kafka.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import grevcev.kafka.event.KafkaEventEnvelope;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, JsonNode> producerFactory(
            KafkaProperties kafkaProperties
    ) {
        Map<String, Object> properties =
                new HashMap<>(kafkaProperties.buildProducerProperties());

        return new DefaultKafkaProducerFactory<>(
                properties,
                new StringSerializer(),
                new JsonSerializer<>()
        );
    }

    @Bean
    public KafkaTemplate<String, JsonNode> kafkaTemplate(
            ProducerFactory<String, JsonNode> producerFactory
    ) {
        return new KafkaTemplate<>(producerFactory);
    }
    @Bean
    public KafkaTemplate<String, Object> deadLetterTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        var serializer = new DelegatingByTypeSerializer(Map.of(
                byte[].class, new ByteArraySerializer(),
                KafkaEventEnvelope.class, new JsonSerializer<>()));
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(properties, new StringSerializer(), serializer));
    }
}