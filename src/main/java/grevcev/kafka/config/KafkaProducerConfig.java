package grevcev.kafka.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, JsonNode> producerFactory(){
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9092");
        return new DefaultKafkaProducerFactory<>(
                properties,
                new StringSerializer(),
                new JacksonJsonSerializer<>()
        );
    }

    @Bean
    public KafkaTemplate<String, JsonNode> kafkaTemplate(
            ProducerFactory<String, JsonNode> producerFactory
    ){
        return new KafkaTemplate<>(producerFactory);
    }
}