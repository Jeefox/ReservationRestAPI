package grevcev.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    @Bean
    public NewTopic reservationsTopic(){
        return TopicBuilder
                .name("reservations")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
