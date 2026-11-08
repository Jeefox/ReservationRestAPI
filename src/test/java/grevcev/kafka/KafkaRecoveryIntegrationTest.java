package grevcev.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import grevcev.AbstractIntegrationTest;
import grevcev.kafka.event.KafkaEventEnvelope;
import grevcev.kafka.event.KafkaEventType;
import grevcev.kafka.event.ReservationCreatedKafkaEvent;
import grevcev.notification.repository.NotificationRepository;
import grevcev.notification.service.NotificationService;
import grevcev.reservation.ReservationStatus;
import grevcev.reservation.model.Reservation;
import grevcev.reservation.repository.ReservationRepository;
import grevcev.room.model.Room;
import grevcev.room.repository.RoomRepository;
import grevcev.user.model.User;
import grevcev.user.model.UserRole;
import grevcev.user.repository.UserRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;

class KafkaRecoveryIntegrationTest extends AbstractIntegrationTest {
    @Autowired private Environment environment;
    @Autowired private ObjectMapper mapper;
    @Autowired private NotificationRepository notifications;
    @Autowired private UserRepository users;
    @Autowired private RoomRepository rooms;
    @Autowired private ReservationRepository reservations;
    @MockitoSpyBean private NotificationService notificationService;

    @Test
    void malformedJsonAndMissingReservationReachDltWithOriginalPayload() throws Exception {
        byte[] malformed = "{broken-json".getBytes(StandardCharsets.UTF_8);
        verifyQuarantine(malformed);
        var event = new ReservationCreatedKafkaEvent(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                LocalDate.now(), LocalDate.now().plusDays(1));
        verifyQuarantine(mapper.writeValueAsBytes(new KafkaEventEnvelope(UUID.randomUUID(),
                KafkaEventType.RESERVATION_CREATED, mapper.valueToTree(event))));
        // A later message still passes after both kinds of poison record.
        sendValid(false);
    }

    @Test
    void transientHandlerFailureRetriesWithoutDuplicateJournalEntries() throws Exception {
        sendValid(true);
    }

    private void sendValid(boolean transientFailure) throws Exception {
        User user = users.save(User.builder().name("Recovery").email(UUID.randomUUID() + "@test.com")
                .password("unused").role(UserRole.USER).build());
        Room room = rooms.save(Room.builder().name("Recovery").capacity(1).build());
        Reservation reservation = reservations.save(Reservation.builder().user(user).room(room)
                .startDate(LocalDate.now().plusDays(10)).endDate(LocalDate.now().plusDays(11))
                .status(ReservationStatus.PENDING).build());
        UUID id = UUID.randomUUID();
        if (transientFailure) {
            doThrow(new TransientDataAccessResourceException("temporary"))
                    .doCallRealMethod().when(notificationService).createReservationCreatedNotification(eq(id), any());
        }
        var event = new ReservationCreatedKafkaEvent(reservation.getId(), user.getId(), room.getId(),
                reservation.getStartDate(), reservation.getEndDate());
        byte[] payload = mapper.writeValueAsBytes(new KafkaEventEnvelope(id, KafkaEventType.RESERVATION_CREATED,
                mapper.valueToTree(event)));
        try (var producer = producer()) {
            producer.send(new ProducerRecord<>("reservations", 0, id.toString(), payload)).get(10, TimeUnit.SECONDS);
            producer.send(new ProducerRecord<>("reservations", 0, id.toString(), payload)).get(10, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                    assertThat(notifications.findByEventId(id)).isPresent());
        }
    }

    private void verifyQuarantine(byte[] payload) throws Exception {
        String key = UUID.randomUUID().toString();
        Map<String, Object> props = Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap(),
                ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new ByteArrayDeserializer());
             var producer = producer()) {
            consumer.subscribe(List.of("reservations.DLT"));
            producer.send(new ProducerRecord<>("reservations", 0, key, payload)).get(10, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    if (key.equals(record.key())) {
                        assertThat(record.value()).isEqualTo(payload);
                        assertThat(record.headers().toArray()).isNotEmpty();
                        return;
                    }
                }
            }
            throw new AssertionError("Record did not reach DLT: " + key);
        }
    }

    private KafkaProducer<String, byte[]> producer() {
        return new KafkaProducer<>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap()),
                new StringSerializer(), new ByteArraySerializer());
    }

    private String bootstrap() {
        return environment.getRequiredProperty("spring.kafka.bootstrap-servers");
    }
}
