package grevcev.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import grevcev.AbstractIntegrationTest;
import grevcev.kafka.event.KafkaEventEnvelope;
import grevcev.kafka.event.KafkaEventType;
import grevcev.kafka.event.ReservationCreatedKafkaEvent;
import grevcev.kafka.handler.ReservationCreatedHandler;
import grevcev.kafka.producer.ReservationKafkaProducer;
import grevcev.kafka.repository.ProcessedEventRepository;
import grevcev.kafka.service.KafkaEventProcessingService;
import grevcev.reservation.ReservationStatus;
import grevcev.reservation.model.Reservation;
import grevcev.reservation.repository.ReservationRepository;
import grevcev.room.model.Room;
import grevcev.room.repository.RoomRepository;
import grevcev.user.model.User;
import grevcev.user.model.UserRole;
import grevcev.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class KafkaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaEventProcessingService processingService;

    @Autowired
    private ReservationKafkaProducer kafkaProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockitoSpyBean
    private ReservationCreatedHandler reservationCreatedHandler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void reservationCreatedEvent_shouldBeConsumedAndProcessedOnlyOnce()
            throws Exception {

        User user = userRepository.save(
                User.builder()
                        .name("Kafka Test User")
                        .email("kafka@test.com")
                        .password("password")
                        .role(UserRole.USER)
                        .build()
        );

        Room room = roomRepository.save(
                Room.builder()
                        .name("Kafka Test Room")
                        .capacity(2)
                        .build()
        );

        Reservation reservation = reservationRepository.save(
                Reservation.builder()
                        .user(user)
                        .room(room)
                        .startDate(LocalDate.of(2027, 1, 10))
                        .endDate(LocalDate.of(2027, 1, 15))
                        .status(ReservationStatus.PENDING)
                        .build()
        );

        UUID eventId = UUID.randomUUID();

        ReservationCreatedKafkaEvent event =
                new ReservationCreatedKafkaEvent(
                        reservation.getId(),
                        user.getId(),
                        room.getId(),
                        reservation.getStartDate(),
                        reservation.getEndDate()
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
                .send(room.getId().toString(), message)
                .get(10, TimeUnit.SECONDS);

        kafkaProducer
                .send(room.getId().toString(), message)
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
    void eventProcessing_shouldRollbackProcessedEvent_whenHandlerFails() throws JsonProcessingException {
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
        assertThatThrownBy(() ->
                processingService.processEvent(envelope)
        ).isInstanceOf(RuntimeException.class);

        assertThat(
                processedEventRepository.existsById(eventId)
        ).isFalse();
    }
}