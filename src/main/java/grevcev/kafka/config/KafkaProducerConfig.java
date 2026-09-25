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

        System.out.println(
                "PRODUCER BOOTSTRAP = " +
                        properties.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)
        );

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
}