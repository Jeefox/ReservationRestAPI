package grevcev.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import grevcev.AbstractIntegrationTest;
import grevcev.kafka.event.KafkaEventEnvelope;
import grevcev.kafka.event.KafkaEventType;
import grevcev.kafka.event.ReservationCreatedKafkaEvent;
import grevcev.notification.model.NotificationEntity;
import grevcev.notification.model.NotificationStatus;
import grevcev.notification.repository.NotificationRepository;
import grevcev.reservation.model.Reservation;
import grevcev.reservation.repository.ReservationRepository;
import grevcev.reservation.ReservationStatus;
import grevcev.room.model.Room;
import grevcev.room.repository.RoomRepository;
import grevcev.user.model.User;
import grevcev.user.model.UserRole;
import grevcev.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class NotificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, JsonNode> kafkaTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreatePendingNotificationForReservationCreatedEvent() {
        // given
        User user = userRepository.save(
                User.builder()
                        .name("Notification Test User")
                        .email("notification@test.com")
                        .password("password")
                        .role(UserRole.USER)
                        .build()
        );

        Room room = roomRepository.save(
                Room.builder()
                        .name("Notification Test Room")
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

        KafkaEventEnvelope envelope = new KafkaEventEnvelope(
                eventId,
                KafkaEventType.RESERVATION_CREATED,
                objectMapper.valueToTree(event)
        );

        JsonNode message = objectMapper.valueToTree(envelope);

// when
        kafkaTemplate.send(
                "reservations",
                room.getId().toString(),
                message
        );

        // then
        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    NotificationEntity notification =
                            notificationRepository.findByEventId(eventId)
                                    .orElseThrow();

                    assertThat(notification.getEventId())
                            .isEqualTo(eventId);

                    assertThat(notification.getUser().getId())
                            .isEqualTo(user.getId());

                    assertThat(notification.getReservation().getId())
                            .isEqualTo(reservation.getId());

                    assertThat(notification.getStatus())
                            .isEqualTo(NotificationStatus.PENDING);

                    assertThat(notification.getSentAt())
                            .isNull();
                });
    }
}