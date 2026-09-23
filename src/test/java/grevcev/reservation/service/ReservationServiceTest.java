package grevcev.reservation.service;

import grevcev.exception.*;
import grevcev.kafka.producer.ReservationKafkaProducer;
import grevcev.outbox.service.OutboxService;
import grevcev.reservation.dto.*;
import grevcev.room.dto.RoomStatsResponse;
import grevcev.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import grevcev.reservation.ReservationStatus;
import grevcev.reservation.event.ReservationCreatedEvent;
import grevcev.reservation.event.ReservationStatusChangedEvent;
import grevcev.reservation.model.Reservation;
import grevcev.room.model.Room;
import grevcev.user.model.User;
import grevcev.reservation.repository.ReservationRepository;
import grevcev.room.repository.RoomRepository;
import grevcev.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ReservationKafkaProducer producer;
    @Mock private OutboxService outboxService;

    @InjectMocks
    private ReservationService reservationService;

    // ============== createReservation ==============

    @Test
    void createReservation_success() {
        // В CreateReservationRequest больше нет userId и status
        CreateReservationRequest request = new CreateReservationRequest(
                2L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(4));

        User user = User.builder().id(1L).name("Ivan").email("ivan@email.com")
                .role(UserRole.USER).build();
        Room room = Room.builder().id(2L).name("luxury").capacity(2).build();

        // Сервис теперь резолвит по EMAIL из токена
        when(userRepository.findByEmail("ivan@email.com")).thenReturn(Optional.of(user));
        when(roomRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(room));
        when(reservationRepository.findConflictingReservations(
                2L, request.startDate(), request.endDate(), ReservationStatus.CANCELLED, null))
                .thenReturn(List.of());

        Reservation saved = Reservation.builder().id(10L).user(user).room(room)
                .startDate(request.startDate()).endDate(request.endDate())
                .status(ReservationStatus.PENDING).build();
        when(reservationRepository.save(any())).thenReturn(saved);

        ReservationResponse response = reservationService.createReservation(request, "ivan@email.com");

        assertEquals(10L, response.id());
        assertEquals("Ivan", response.userName());
        assertEquals("luxury", response.roomName());
        assertEquals(ReservationStatus.PENDING, response.status());
        verify(reservationRepository).save(any());
        verify(eventPublisher).publishEvent(any(ReservationCreatedEvent.class));
    }

    @Test
    void createReservation_userNotFound() {
        CreateReservationRequest request = new CreateReservationRequest(
                2L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(4));

        when(userRepository.findByEmail("fake@email.com")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> reservationService.createReservation(request, "fake@email.com"));
    }

    @Test
    void createReservation_roomNotFound() {
        CreateReservationRequest request = new CreateReservationRequest(
                2L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(4));

        User user = User.builder().id(1L).name("Ivan").email("ivan@email.com")
                .role(UserRole.USER).build();

        when(userRepository.findByEmail("ivan@email.com")).thenReturn(Optional.of(user));
        when(roomRepository.findByIdForUpdate(2L)).thenReturn(Optional.empty());

        assertThrows(RoomNotFoundException.class,
                () -> reservationService.createReservation(request, "ivan@email.com"));
    }

    @Test
    void createReservation_roomAlreadyBooked() {
        CreateReservationRequest request = new CreateReservationRequest(
                2L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(4));

        User user = User.builder().id(1L).name("Ivan").email("ivan@email.com")
                .role(UserRole.USER).build();
        Room room = Room.builder().id(2L).name("luxury").capacity(2).build();

        User otherUser = User.builder().id(99L).name("Other").email("other@email.com")
                .role(UserRole.USER).build();
        Reservation conflicting = Reservation.builder()
                .id(5L).user(otherUser).room(room)
                .startDate(LocalDate.now().plusDays(2))
                .endDate(LocalDate.now().plusDays(5))
                .status(ReservationStatus.PENDING)
                .build();

        when(userRepository.findByEmail("ivan@email.com")).thenReturn(Optional.of(user));
        when(roomRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(room));
        when(reservationRepository.findConflictingReservations(
                2L, request.startDate(), request.endDate(), ReservationStatus.CANCELLED, null))
                .thenReturn(List.of(conflicting));

        assertThrows(RoomAlreadyBookedException.class,
                () -> reservationService.createReservation(request, "ivan@email.com"));

        verify(reservationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createReservation_noConflict() {
        CreateReservationRequest request = new CreateReservationRequest(
                2L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(4));

        User user = User.builder().id(1L).name("Ivan").email("ivan@email.com")
                .role(UserRole.USER).build();
        Room room = Room.builder().id(2L).name("luxury").capacity(2).build();

        when(userRepository.findByEmail("ivan@email.com")).thenReturn(Optional.of(user));
        when(roomRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(room));
        when(reservationRepository.findConflictingReservations(
                2L, request.startDate(), request.endDate(), ReservationStatus.CANCELLED, null))
                .thenReturn(List.of());

        Reservation saved = Reservation.builder().id(10L).user(user).room(room)
                .startDate(request.startDate()).endDate(request.endDate())
                .status(ReservationStatus.PENDING).build();
        when(reservationRepository.save(any())).thenReturn(saved);

        ReservationResponse response = reservationService.createReservation(request, "ivan@email.com");

        assertEquals(10L, response.id());
        verify(reservationRepository).save(any());
    }

    // ============== getReservationById ==============

    @Test
    void getReservationById_success() {
        User user = User.builder().id(1L).name("Ivan").email("ivan@email.com")
                .role(UserRole.USER).build();
        Room room = Room.builder().id(2L).name("luxury").capacity(2).build();
        Reservation reservation = Reservation.builder().id(10L).user(user).room(room)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .status(ReservationStatus.PENDING)
                .build();

        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));

        ReservationResponse response = reservationService.getReservationById(10L);

        assertEquals(10L, response.id());
        assertEquals("Ivan", response.userName());
    }

    @Test
    void getReservationById_notFound() {
        when(reservationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ReservationNotFoundException.class,
                () -> reservationService.getReservationById(999L));
    }

    // ============== updateReservation ==============

    @Test
    void updateReservation_sameReservationNoConflict() {
        // В UpdateReservationRequest больше нет userId и status
        UpdateReservationRequest request = new UpdateReservationRequest(
                2L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));

        User user = User.builder().id(1L).name("Ivan").email("ivan@email.com")
                .role(UserRole.USER).build();
        Room room = Room.builder().id(2L).name("luxury").capacity(2).build();
        Reservation reservation = Reservation.builder()
                .id(10L).user(user).room(room)
                .status(ReservationStatus.PENDING)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .build();

        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        when(userRepository.findByEmail("ivan@email.com")).thenReturn(Optional.of(user));
        when(roomRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(room));
        when(reservationRepository.findConflictingReservations(
                2L, request.startDate(), request.endDate(), ReservationStatus.CANCELLED, 10L))
                .thenReturn(List.of());

        reservationService.updateReservation(10L, request, "ivan@email.com");

        verify(reservationRepository).findConflictingReservations(
                eq(2L), any(), any(), eq(ReservationStatus.CANCELLED), eq(10L));
    }

    @Test
    void updateReservation_notOwner_throwsAccessDenied() {
        UpdateReservationRequest request = new UpdateReservationRequest(
                2L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));

        User owner = User.builder().id(1L).name("Ivan").email("ivan@email.com")
                .role(UserRole.USER).build();
        User otherUser = User.builder().id(99L).name("Other").email("other@email.com")
                .role(UserRole.USER).build();
        Room room = Room.builder().id(2L).name("luxury").capacity(2).build();

        Reservation reservation = Reservation.builder()
                .id(10L).user(owner).room(room)
                .status(ReservationStatus.PENDING)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .build();

        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        when(userRepository.findByEmail("other@email.com")).thenReturn(Optional.of(otherUser));

        // other пытается править чужую бронь → 403
        assertThrows(AccessDeniedException.class,
                () -> reservationService.updateReservation(10L, request, "other@email.com"));
    }

    @Test
    void updateReservation_conflictWithOver() {
        UpdateReservationRequest request = new UpdateReservationRequest(
                4L, LocalDate.now().plusDays(2), LocalDate.now().plusDays(4));

        User user = User.builder().id(1L).name("Ivan").email("ivan@email.com")
                .role(UserRole.USER).build();
        Room room = Room.builder().id(2L).name("luxury").capacity(2).build();
        Reservation reservation = Reservation.builder()
                .id(10L).user(user).room(room)
                .status(ReservationStatus.PENDING)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .build();

        Room newRoom = Room.builder().id(4L).name("newRoom").capacity(3).build();

        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        when(userRepository.findByEmail("ivan@email.com")).thenReturn(Optional.of(user));
        when(roomRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(newRoom));

        User otherUser = User.builder().id(99L).name("Other").email("other@email.com")
                .role(UserRole.USER).build();
        Reservation conflicting = Reservation.builder()
                .id(5L).user(otherUser).room(newRoom)
                .startDate(LocalDate.now().plusDays(2))
                .endDate(LocalDate.now().plusDays(5))
                .status(ReservationStatus.PENDING)
                .build();

        when(reservationRepository.findConflictingReservations(
                4L, request.startDate(), request.endDate(), ReservationStatus.CANCELLED, 10L))
                .thenReturn(List.of(conflicting));

        assertThrows(RoomAlreadyBookedException.class,
                () -> reservationService.updateReservation(10L, request, "ivan@email.com"));
    }

    // ============== deleteReservation ==============

    @Test
    void deleteReservation_success() {
        User user = User.builder().id(1L).name("Ivan").email("ivan@email.com")
                .role(UserRole.USER).build();
        Room room = Room.builder().id(2L).name("luxury").capacity(2).build();
        Reservation reservation = Reservation.builder().id(10L).user(user).room(room)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .status(ReservationStatus.PENDING)
                .build();

        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        when(userRepository.findByEmail("ivan@email.com")).thenReturn(Optional.of(user));

        reservationService.deleteReservationById(10L, "ivan@email.com");

        verify(reservationRepository).delete(reservation);
    }

    @Test
    void deleteReservation_notOwner_throwsAccessDenied() {
        User owner = User.builder().id(1L).name("Ivan").email("ivan@email.com")
                .role(UserRole.USER).build();
        User otherUser = User.builder().id(99L).name("Other").email("other@email.com")
                .role(UserRole.USER).build();
        Room room = Room.builder().id(2L).name("luxury").capacity(2).build();

        Reservation reservation = Reservation.builder().id(10L).user(owner).room(room)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .status(ReservationStatus.PENDING)
                .build();

        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        when(userRepository.findByEmail("other@email.com")).thenReturn(Optional.of(otherUser));

        assertThrows(AccessDeniedException.class,
                () -> reservationService.deleteReservationById(10L, "other@email.com"));

        verify(reservationRepository, never()).delete(any(Reservation.class));
    }

    @Test
    void deleteReservation_notFound() {
        when(reservationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ReservationNotFoundException.class,
                () -> reservationService.deleteReservationById(999L, "ivan@email.com"));

        verify(reservationRepository, never()).delete(any(Reservation.class));
    }

    @Test
    void deleteReservation_adminCanDeleteOthers() {
        User owner = User.builder().id(1L).name("Ivan").email("ivan@email.com")
                .role(UserRole.USER).build();
        User admin = User.builder().id(99L).name("Admin").email("admin@admin.com")
                .role(UserRole.ADMIN).build();
        Room room = Room.builder().id(2L).name("luxury").capacity(2).build();

        Reservation reservation = Reservation.builder().id(10L).user(owner).room(room)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .status(ReservationStatus.PENDING)
                .build();

        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        when(userRepository.findByEmail("admin@admin.com")).thenReturn(Optional.of(admin));

        reservationService.deleteReservationById(10L, "admin@admin.com");

        verify(reservationRepository).delete(reservation);
    }

    // ============== changeStatus ==============

    @Test
    void changeStatus_adminApproves_success() {
        UpdateStatusRequest request = new UpdateStatusRequest(ReservationStatus.APPROVED);
        User owner = User.builder().id(1L).name("Ivan").email("ivan@email.com")
                .role(UserRole.USER).build();
        User admin = User.builder().id(99L).name("Admin").email("admin@admin.com")
                .role(UserRole.ADMIN).build();
        Room room = Room.builder().id(2L).name("luxury").capacity(2).build();

        Reservation reservation = Reservation.builder()
                .id(1L).user(owner).room(room)
                .status(ReservationStatus.PENDING)
                .build();

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(userRepository.findByEmail("admin@admin.com")).thenReturn(Optional.of(admin));

        ReservationResponse response = reservationService.changeStatus(1L, request, "admin@admin.com");

        assertEquals(ReservationStatus.APPROVED, reservation.getStatus());
        assertEquals(ReservationStatus.APPROVED, response.status());
        verify(eventPublisher).publishEvent(any(ReservationStatusChangedEvent.class));
    }

    @Test
    void changeStatus_userCannotApprove_throwsAccessDenied() {
        UpdateStatusRequest request = new UpdateStatusRequest(ReservationStatus.APPROVED);
        User owner = User.builder().id(1L).name("Ivan").email("ivan@email.com")
                .role(UserRole.USER).build();
        Room room = Room.builder().id(2L).name("luxury").capacity(2).build();

        Reservation reservation = Reservation.builder()
                .id(1L).user(owner).room(room)
                .status(ReservationStatus.PENDING)
                .build();

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(userRepository.findByEmail("ivan@email.com")).thenReturn(Optional.of(owner));

        // Даже владелец-USER не может одобрять — только админ
        assertThrows(AccessDeniedException.class,
                () -> reservationService.changeStatus(1L, request, "ivan@email.com"));

        assertEquals(ReservationStatus.PENDING, reservation.getStatus());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_ownerCancelsOwn_success() {
        UpdateStatusRequest request = new UpdateStatusRequest(ReservationStatus.CANCELLED);
        User owner = User.builder().id(1L).name("Ivan").email("ivan@email.com")
                .role(UserRole.USER).build();
        Room room = Room.builder().id(2L).name("luxury").capacity(2).build();

        Reservation reservation = Reservation.builder()
                .id(1L).user(owner).room(room)
                .status(ReservationStatus.PENDING)
                .build();

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(userRepository.findByEmail("ivan@email.com")).thenReturn(Optional.of(owner));

        ReservationResponse response = reservationService.changeStatus(1L, request, "ivan@email.com");

        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertEquals(ReservationStatus.CANCELLED, response.status());
        verify(eventPublisher).publishEvent(any(ReservationStatusChangedEvent.class));
    }

    @Test
    void changeStatus_userCannotCancelOthers_throwsAccessDenied() {
        UpdateStatusRequest request = new UpdateStatusRequest(ReservationStatus.CANCELLED);
        User owner = User.builder().id(1L).name("Ivan").email("ivan@email.com")
                .role(UserRole.USER).build();
        User otherUser = User.builder().id(99L).name("Other").email("other@email.com")
                .role(UserRole.USER).build();
        Room room = Room.builder().id(2L).name("luxury").capacity(2).build();

        Reservation reservation = Reservation.builder()
                .id(1L).user(owner).room(room)
                .status(ReservationStatus.PENDING)
                .build();

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(userRepository.findByEmail("other@email.com")).thenReturn(Optional.of(otherUser));

        assertThrows(AccessDeniedException.class,
                () -> reservationService.changeStatus(1L, request, "other@email.com"));

        assertEquals(ReservationStatus.PENDING, reservation.getStatus());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_invalidTransition_throwsFirst() {
        // CANCELLED → APPROVED запрещен FSM'ом ДО проверки ролей
        UpdateStatusRequest request = new UpdateStatusRequest(ReservationStatus.APPROVED);
        User user = User.builder().id(1L).name("Ivan").email("ivan@email.com")
                .role(UserRole.USER).build();
        Room room = Room.builder().id(2L).name("luxury").capacity(2).build();

        Reservation reservation = Reservation.builder()
                .id(1L).user(user).room(room)
                .status(ReservationStatus.CANCELLED)
                .build();

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        assertThrows(InvalidStatusTransitionException.class,
                () -> reservationService.changeStatus(1L, request, "ivan@email.com"));

        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ============== getRoomStats ==============

    @Test
    void getRoomStats_success() {
        LocalDate from = LocalDate.of(2026, 12, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);

        when(reservationRepository.getStats(from, to))
                .thenReturn(List.of(new RoomStatsResponse(1L, "luxury", 3L)));

        List<RoomStatsResponse> result = reservationService.getStats(from, to);

        assertEquals(1, result.size());
        assertEquals("luxury", result.get(0).roomName());
        assertEquals(3L, result.get(0).bookingCount());
        verify(reservationRepository).getStats(from, to);
    }
}