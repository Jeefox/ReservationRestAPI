package grevcev.kafka.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import grevcev.kafka.event.ReservationCreatedKafkaEvent;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, ReservationCreatedKafkaEvent> consumerFactory(){
        Map<String, Object> properties = new HashMap<>();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9092"
        );

        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );

        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JacksonJsonDeserializer.class
        );

        JacksonJsonDeserializer<ReservationCreatedKafkaEvent> deserializer =
                new JacksonJsonDeserializer<>(ReservationCreatedKafkaEvent.class);

        deserializer.addTrustedPackages("school.grevcev.reservation.event");

        return new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ReservationCreatedKafkaEvent>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, ReservationCreatedKafkaEvent> consumerFactory
    ) {
        var factory =
                new ConcurrentKafkaListenerContainerFactory<String, ReservationCreatedKafkaEvent>();

        factory.setConsumerFactory(consumerFactory);

        return factory;
    }
}
