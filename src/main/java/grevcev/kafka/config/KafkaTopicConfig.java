package grevcev.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    @Value("${app.kafka.topic:reservations}")
    private String topic;
    @Value("${app.kafka.partitions:3}")
    private int partitions;
    @Value("${app.kafka.replicas:1}")
    private int replicas;

    @Bean
    public NewTopic reservationsTopic() {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic reservationsDeadLetterTopic() {
        return TopicBuilder.name(topic + ".DLT").partitions(partitions).replicas(replicas).build();
    }
}
