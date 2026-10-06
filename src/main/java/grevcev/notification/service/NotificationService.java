package grevcev.notification.service;

import grevcev.kafka.event.ReservationCreatedKafkaEvent;
import grevcev.notification.model.NotificationEntity;
import grevcev.notification.model.NotificationStatus;
import grevcev.notification.repository.NotificationRepository;
import grevcev.reservation.ReservationStatus;
import grevcev.reservation.model.Reservation;
import grevcev.reservation.repository.ReservationRepository;
import grevcev.user.model.User;
import grevcev.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class NotificationService {
    private final static Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final NotificationRepository notificationRepository;

    public NotificationService(UserRepository userRepository, ReservationRepository reservationRepository,
                               NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void createReservationCreatedNotification(UUID eventId, ReservationCreatedKafkaEvent event) {
        Reservation reservation = reservationRepository.findById(event.reservationId())
                .orElseThrow(()->new RuntimeException("Reservation not found!"));

        if (reservation.getStatus() == ReservationStatus.DELETED){
            log.info("Skipping notification creation for DELETED reservation: {}", event.reservationId());
            return;
        }

        User user = userRepository.findById(event.userId())
                .orElseThrow(()-> new RuntimeException("User not found!"));

        NotificationEntity notification = NotificationEntity.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .user(user)
                .reservation(reservation)
                .createdAt(LocalDateTime.now())
                .status(NotificationStatus.PENDING)
                .build();

        notificationRepository.save(notification);
    }
}