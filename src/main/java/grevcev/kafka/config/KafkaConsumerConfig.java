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
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.common.TopicPartition;

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

        return new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deserializer)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, KafkaEventEnvelope>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, KafkaEventEnvelope> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler
    ) {
        var factory =
                new ConcurrentKafkaListenerContainerFactory<String, KafkaEventEnvelope>();

        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);

        return factory;
    }
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            @Qualifier("deadLetterTemplate") KafkaTemplate<String, Object> template,
            @Value("${app.kafka.retry-delay-ms:1000}") long delay,
            @Value("${app.kafka.retry-attempts:2}") long retries) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(delay, retries));
    }
}
