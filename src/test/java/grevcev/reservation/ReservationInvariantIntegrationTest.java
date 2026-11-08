package grevcev.reservation;

import grevcev.AbstractIntegrationTest;
import grevcev.auth.service.JwtService;
import grevcev.reservation.dto.UpdateReservationRequest;
import grevcev.reservation.dto.UpdateStatusRequest;
import grevcev.reservation.model.Reservation;
import grevcev.reservation.repository.ReservationRepository;
import grevcev.reservation.service.ReservationService;
import grevcev.room.model.Room;
import grevcev.room.repository.RoomRepository;
import grevcev.user.model.User;
import grevcev.user.model.UserRole;
import grevcev.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Transactional
class ReservationInvariantIntegrationTest extends AbstractIntegrationTest {
    @Autowired private ReservationRepository reservations;
    @Autowired private UserRepository users;
    @Autowired private RoomRepository rooms;
    @Autowired private ReservationService service;
    @Autowired private JwtService jwt;
    @Autowired private MockMvc mvc;

    private User owner;
    private User admin;
    private Room room;
    private String token;
    private String adminToken;
    private final LocalDate day = LocalDate.now().plusDays(30);

    @BeforeEach
    void setup() {
        owner = user(UserRole.USER);
        admin = user(UserRole.ADMIN);
        room = rooms.save(Room.builder().name("Invariant room").capacity(2).build());
        token = jwt.generateToken(owner.getEmail(), "USER");
        adminToken = jwt.generateToken(admin.getEmail(), "ADMIN");
    }

    @Test
    void searchAppliesEachBoundAndPaginationOnRealDatabase() {
        Reservation before = reservation(day.minusDays(2), day, ReservationStatus.PENDING);
        Reservation crossing = reservation(day.minusDays(1), day.plusDays(1), ReservationStatus.APPROVED);
        Reservation after = reservation(day.plusDays(1), day.plusDays(3), ReservationStatus.PENDING);
        var page = PageRequest.of(0, 10, Sort.by("startDate"));
        assertThat(service.search(null, room.getId(), null, day, null, page, owner.getEmail()).getContent())
                .extracting(r -> r.id()).containsExactly(crossing.getId(), after.getId());
        assertThat(service.search(null, room.getId(), null, null, day, page, owner.getEmail()).getContent())
                .extracting(r -> r.id()).containsExactly(before.getId(), crossing.getId());
        assertThat(service.search(null, room.getId(), null, day, day.plusDays(1), page, owner.getEmail()).getContent())
                .extracting(r -> r.id()).containsExactly(crossing.getId());
        var limited = service.search(null, room.getId(), null, null, null,
                PageRequest.of(1, 1, Sort.by("startDate").descending()), owner.getEmail());
        assertThat(limited.getTotalElements()).isEqualTo(3);
        assertThat(limited.getContent()).extracting(r -> r.id()).containsExactly(crossing.getId());
        assertThat(service.search(null, room.getId(), ReservationStatus.APPROVED, null, null, page, owner.getEmail())
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void invalidSearchIntervalReturns400() throws Exception {
        for (LocalDate end : new LocalDate[]{day, day.minusDays(1)}) {
            mvc.perform(get("/api/v1/reservations").header("Authorization", "Bearer " + token)
                            .param("from", day.toString()).param("to", end.toString()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void statsAndConflictsIgnoreTerminalStatusesAndTouchingBoundaries() {
        reservation(day.minusDays(1), day, ReservationStatus.PENDING);
        reservation(day, day.plusDays(2), ReservationStatus.CANCELLED);
        reservation(day, day.plusDays(2), ReservationStatus.DELETED);
        reservation(day.plusDays(2), day.plusDays(3), ReservationStatus.APPROVED);
        assertThat(reservations.existsConflictingReservation(room.getId(), day, day.plusDays(2), null)).isFalse();
        assertThat(service.getStats(day, day.plusDays(2))).noneMatch(s -> s.roomId().equals(room.getId()));
        assertThat(reservations.existsConflictingReservation(room.getId(), day.plusDays(1), day.plusDays(3), null)).isTrue();
    }

    @Test
    void changingApprovedDatesRequiresApprovalAgain() {
        Reservation booked = reservation(day, day.plusDays(2), ReservationStatus.APPROVED);
        service.updateReservation(booked.getId(), new UpdateReservationRequest(room.getId(), day.plusDays(3),
                day.plusDays(5)), owner.getEmail());
        reservations.flush();
        assertThat(booked.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void historicalReservationCanStillBeCancelled() {
        Reservation booked = reservation(day.minusDays(40), day.minusDays(35), ReservationStatus.APPROVED);
        service.changeStatus(booked.getId(), new UpdateStatusRequest(ReservationStatus.CANCELLED), owner.getEmail());
        reservations.flush();
        assertThat(booked.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancelledReservationCannotBeEdited() throws Exception {
        Reservation booked = reservation(day, day.plusDays(2), ReservationStatus.CANCELLED);
        mvc.perform(put("/api/v1/reservations/" + booked.getId()).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(booking(day, day.plusDays(2))))
                .andExpect(status().isConflict());
        assertThat(booked.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void invalidDatesAreRejectedBeforePersistence() throws Exception {
        for (LocalDate end : new LocalDate[]{day, day.minusDays(1)}) {
            mvc.perform(post("/api/v1/reservations").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(booking(day, end)))
                    .andExpect(status().isBadRequest());
        }
        Reservation booked = reservation(day, day.plusDays(2), ReservationStatus.PENDING);
        mvc.perform(put("/api/v1/reservations/" + booked.getId()).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(booking(LocalDate.now().minusDays(1), day)))
                .andExpect(status().isBadRequest());
        assertThat(booked.getStartDate()).isEqualTo(day);
    }

    @Test
    void roomCapacityIsRequiredAndPositive() throws Exception {
        for (String capacity : new String[]{"null", "0", "-1"}) {
            mvc.perform(put("/api/v1/rooms/" + room.getId()).header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Changed\",\"capacity\":" + capacity + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.capacity").exists());
        }
        assertThat(rooms.findById(room.getId()).orElseThrow().getCapacity()).isEqualTo(2);
    }

    @Test
    void userDirectoryAndProfilesRespectPrivacyAndDeprecatedPostIsGone() throws Exception {
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/v1/users/" + admin.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/" + owner.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void missingAndMalformedTokensReturnConsistent401() throws Exception {
        mvc.perform(get("/api/v1/rooms"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").exists());
        mvc.perform(get("/api/v1/rooms").header("Authorization", "Bearer broken"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Autowired private grevcev.kafka.service.KafkaEventProcessingService processing;
    @Autowired private grevcev.notification.repository.NotificationRepository notifications;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper mapper;

    @Test
    void createdEventAfterSoftDeleteIsJournaledExactlyOnce() {
        Reservation booked = reservation(day, day.plusDays(2), ReservationStatus.PENDING);
        service.deleteReservationById(booked.getId(), owner.getEmail());
        reservations.flush();
        UUID eventId = UUID.randomUUID();
        var payload = new grevcev.kafka.event.ReservationCreatedKafkaEvent(booked.getId(), owner.getId(), room.getId(),
                booked.getStartDate(), booked.getEndDate());
        var envelope = new grevcev.kafka.event.KafkaEventEnvelope(eventId,
                grevcev.kafka.event.KafkaEventType.RESERVATION_CREATED, mapper.valueToTree(payload));
        processing.processEvent(envelope);
        processing.processEvent(envelope);
        assertThat(notifications.findByEventId(eventId)).isPresent();
        assertThat(reservations.findById(booked.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.DELETED);
    }

    private User user(UserRole role) {
        return users.save(User.builder().name("Test").email(UUID.randomUUID() + "@test.com")
                .password("unused-hash").role(role).build());
    }

    private Reservation reservation(LocalDate start, LocalDate end, ReservationStatus status) {
        return reservations.saveAndFlush(Reservation.builder().user(owner).room(room)
                .startDate(start).endDate(end).status(status).build());
    }

    private String booking(LocalDate start, LocalDate end) {
        return "{\"roomId\":" + room.getId() + ",\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\"}";
    }
}
