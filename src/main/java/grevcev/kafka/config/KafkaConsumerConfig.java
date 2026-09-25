package grevcev.kafka.config;

import grevcev.kafka.event.KafkaEventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, KafkaEventEnvelope> consumerFactory(
            KafkaProperties kafkaProperties
    ) {

        Map<String, Object> properties =
                new HashMap<>(kafkaProperties.buildConsumerProperties());

        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        JsonDeserializer<KafkaEventEnvelope> deserializer =
                new JsonDeserializer<>(KafkaEventEnvelope.class, false);

        deserializer.addTrustedPackages("grevcev.kafka.event");

        System.out.println(
                "CONSUMER BOOTSTRAP = " +
                        properties.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)
        );

        return new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, KafkaEventEnvelope>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, KafkaEventEnvelope> consumerFactory
    ) {
        var factory =
                new ConcurrentKafkaListenerContainerFactory<String, KafkaEventEnvelope>();

        factory.setConsumerFactory(consumerFactory);

        return factory;
    }
}
