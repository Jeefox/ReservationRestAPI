package grevcev.reservation.service;

import grevcev.exception.*;
import grevcev.kafka.producer.ReservationKafkaProducer;
import grevcev.outbox.service.OutboxService;
import grevcev.reservation.dto.*;
import grevcev.room.dto.RoomStatsResponse;
import grevcev.user.model.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import grevcev.reservation.ReservationStatus;
import grevcev.kafka.event.ReservationCreatedKafkaEvent;
import grevcev.reservation.event.ReservationStatusChangedEvent;
import grevcev.reservation.event.ReservationCreatedEvent;
import grevcev.reservation.model.Reservation;
import grevcev.reservation.model.ReservationSpecifications;
import grevcev.room.model.Room;
import grevcev.user.model.User;
import grevcev.reservation.repository.ReservationRepository;
import grevcev.room.repository.RoomRepository;
import grevcev.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final OutboxService outboxService;
    public ReservationService (ReservationRepository reservationRepository, UserRepository userRepository,
                               RoomRepository roomRepository,  ApplicationEventPublisher applicationEventPublisher,
                               OutboxService outboxService) {
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.outboxService = outboxService;
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(Long id) {
        Reservation reservation = reservationRepository.findById(id).orElseThrow(()->new ReservationNotFoundException(id));
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse createReservation(CreateReservationRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(()->new UserNotFoundException());
        Room room = roomRepository.findByIdForUpdate(request.roomId()).orElseThrow(()->new RoomNotFoundException(request.roomId()));

        List<Reservation> conflicting = reservationRepository.findConflictingReservations(room.getId(), request.startDate(),
                request.endDate(), ReservationStatus.CANCELLED, null);
        if(!conflicting.isEmpty()) throw new RoomAlreadyBookedException(room.getId(), request.startDate(), request.endDate());

        Reservation reservation = Reservation.builder()
                .user(user)
                .room(room)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(ReservationStatus.PENDING)
                .build();

        Reservation savedReservation = reservationRepository.save(reservation);
        applicationEventPublisher.publishEvent(new ReservationCreatedEvent(savedReservation.getId(),
                savedReservation.getRoom().getId(), savedReservation.getStartDate(), savedReservation.getEndDate()));

        outboxService.createReservationCreatedEvent(savedReservation);
        return toResponse(savedReservation);
    }

    private ReservationResponse toResponse(Reservation savedReservation) {
        return new ReservationResponse(
                savedReservation.getId(),
                savedReservation.getUser().getId(),
                savedReservation.getUser().getName(),
                savedReservation.getRoom().getId(),
                savedReservation.getRoom().getName(),
                savedReservation.getStartDate(),
                savedReservation.getEndDate(),
                savedReservation.getStatus()
        );
    }

    @Transactional
    public ReservationResponse updateReservation(Long id, UpdateReservationRequest request, String email) {
        Reservation found = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);

        boolean isOwner = found.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
        if (!isOwner && !isAdmin) throw new AccessDeniedException("Вы не владелец этой брони");

        Room room = roomRepository.findByIdForUpdate(request.roomId())
                .orElseThrow(() -> new RoomNotFoundException(request.roomId()));

        List<Reservation> conflicting = reservationRepository.findConflictingReservations(request.roomId(), request.startDate(),
                request.endDate(), ReservationStatus.CANCELLED, id);

        if(!conflicting.isEmpty()) throw new RoomAlreadyBookedException(request.roomId(), request.startDate(), request.endDate());

        found.setRoom(room);
        found.setStartDate(request.startDate());
        found.setEndDate(request.endDate());

        return toResponse(found);
    }

    @Transactional
    public void deleteReservationById(Long id, String email) {
        Reservation found = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);

        boolean isOwner = found.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
        if (!isOwner && !isAdmin) throw new AccessDeniedException("Вы не владелец этой брони");

        reservationRepository.delete(found);
    }

    @Transactional(readOnly = true)
    public Page<ReservationResponse> search(Long userId, Long roomId, ReservationStatus status,
                                            LocalDate from, LocalDate to, Pageable pageable, String email) {

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);
        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;

        List<Specification<Reservation>> specifications = new ArrayList<>();

        // Ключевая логика:
        if (userId != null) {
            // если userId передан — ADMIN видит все, USER только свои
            Long effectiveUserId = isAdmin ? userId : currentUser.getId();
            specifications.add(ReservationSpecifications.hasUserId(effectiveUserId));
        } else if (!isAdmin) {
            // USER без userId → только свои
            specifications.add(ReservationSpecifications.hasUserId(currentUser.getId()));
        }
        // ADMIN без userId → видит все (спецификация не добавляется)

        if (roomId != null) specifications.add(ReservationSpecifications.hasRoomId(roomId));
        if (status != null) specifications.add(ReservationSpecifications.hasStatus(status));
        if (from != null && to != null) specifications.add(ReservationSpecifications.overlapsWith(from, to));

        return reservationRepository.findAllBy(Specification.allOf(specifications), pageable).map(this::toResponse);
    }

    @Transactional
    public ReservationResponse changeStatus(Long id, UpdateStatusRequest request, String email) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        ReservationStatus currentStatus = reservation.getStatus();
        if (!currentStatus.canTransitionTo(request.status())) {
            throw new InvalidStatusTransitionException(currentStatus, request.status());
        }

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);

        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
        boolean isOwner = reservation.getUser().getId().equals(currentUser.getId());

        if (ReservationStatus.isApproverRequired(currentStatus, request.status())) {
            if (!isAdmin) throw new AccessDeniedException("Только админ может одобрять брони");
        } else {
            if (!isOwner && !isAdmin) throw new AccessDeniedException("Вы не владелец этой брони");
        }

        reservation.setStatus(request.status());
        log.info("Reservation {} status changed: {} -> {}", id, currentStatus, request.status());
        applicationEventPublisher.publishEvent(new ReservationStatusChangedEvent(reservation.getId(), currentStatus, reservation.getStatus()));

        return toResponse(reservation);
    }

    @Transactional
    public List<RoomStatsResponse> getStats(LocalDate from, LocalDate to) {
        return  reservationRepository.getStats(from, to);
    }
}
