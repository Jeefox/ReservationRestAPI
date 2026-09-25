package grevcev.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import grevcev.kafka.event.KafkaEventEnvelope;
import grevcev.kafka.event.KafkaEventType;
import grevcev.kafka.event.ReservationCreatedKafkaEvent;
import grevcev.kafka.handler.ReservationCreatedHandler;
import grevcev.kafka.producer.ReservationKafkaProducer;
import grevcev.kafka.repository.ProcessedEventRepository;
import grevcev.kafka.service.KafkaEventProcessingService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class KafkaIntegrationTest {

    @Autowired
    private KafkaEventProcessingService processingService;

    @Container
    static KafkaContainer kafka =
            new KafkaContainer("apache/kafka-native:3.8.0");

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {

        registry.add(
                "spring.kafka.bootstrap-servers",
                kafka::getBootstrapServers
        );

        registry.add(
                "spring.kafka.consumer.bootstrap-servers",
                kafka::getBootstrapServers
        );

        registry.add(
                "spring.kafka.producer.bootstrap-servers",
                kafka::getBootstrapServers
        );

        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );

        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );

        registry.add(
                "spring.datasource.driver-class-name",
                postgres::getDriverClassName
        );

        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "validate"
        );
    }

    @Autowired
    private ReservationKafkaProducer kafkaProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockitoSpyBean
    private ReservationCreatedHandler reservationCreatedHandler;

    @Test
    void reservationCreatedEvent_shouldBeConsumedAndProcessedOnlyOnce()
            throws Exception {

        UUID eventId = UUID.randomUUID();

        ReservationCreatedKafkaEvent event =
                new ReservationCreatedKafkaEvent(
                        1L,
                        2L,
                        3L,
                        LocalDate.of(2027, 1, 10),
                        LocalDate.of(2027, 1, 15)
                );

        JsonNode payload = objectMapper.valueToTree(event);

        KafkaEventEnvelope envelope =
                new KafkaEventEnvelope(
                        eventId,
                        KafkaEventType.RESERVATION_CREATED,
                        payload
                );

        JsonNode message = objectMapper.valueToTree(envelope);

        System.out.println(message);

        kafkaProducer
                .send("3", message)
                .get(10, TimeUnit.SECONDS);

        kafkaProducer
                .send("3", message)
                .get(10, TimeUnit.SECONDS);

        org.awaitility.Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(
                                processedEventRepository.existsById(eventId)
                        ).isTrue()
                );

        verify(
                reservationCreatedHandler,
                timeout(10_000).times(1)
        ).handle(any(KafkaEventEnvelope.class));
    }

    @Test
    void eventProcessing_shouldRollbackProcessedEvent_whenHandlerFails() {
        UUID eventId = UUID.randomUUID();

        ReservationCreatedKafkaEvent event =
                new ReservationCreatedKafkaEvent(
                        1L,
                        2L,
                        3L,
                        LocalDate.of(2027, 1, 10),
                        LocalDate.of(2027, 1, 15)
                );

        JsonNode payload = objectMapper.valueToTree(event);

        KafkaEventEnvelope envelope =
                new KafkaEventEnvelope(
                        eventId,
                        KafkaEventType.RESERVATION_CREATED,
                        payload
                );

        doThrow(new RuntimeException("Test exception"))
                .when(reservationCreatedHandler)
                .handle(any(KafkaEventEnvelope.class));

        assertThatThrownBy(() ->
                processingService.processEvent(envelope)
        ).isInstanceOf(RuntimeException.class);

        assertThat(
                processedEventRepository.existsById(eventId)
        ).isFalse();
    }

}