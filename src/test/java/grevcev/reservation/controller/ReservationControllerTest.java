package grevcev.reservation.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import grevcev.reservation.ReservationStatus;
import grevcev.reservation.dto.ReservationResponse;
import grevcev.room.dto.RoomStatsResponse;
import grevcev.exception.InvalidStatusTransitionException;
import grevcev.exception.ReservationNotFoundException;
import grevcev.auth.security.SecurityConfig;
import grevcev.auth.service.JwtService;
import grevcev.reservation.service.ReservationService;
import grevcev.room.service.RoomService;
import grevcev.user.service.UserService;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private RoomService roomService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void createReservation_returns201WithLocation() throws Exception {
        // СНАЧАЛА when, ПОТОМ perform
        when(reservationService.createReservation(any(), eq("ivan@email.com"))).thenReturn(
                new ReservationResponse(10L, 1L, "Ivan", 2L, "luxury",
                        LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5),
                        ReservationStatus.PENDING));

        mockMvc.perform(post("/api/v1/reservations")
                        .with(ivan())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomId":2,
                                 "startDate":"2026-12-01","endDate":"2026-12-05"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.userName").value("Ivan"));
    }

    @Test
    void createReservation_returns400WithNoEndDate() throws Exception {
        // Валидация срабатывает до контроллера — мок не нужен
        mockMvc.perform(post("/api/v1/reservations")
                        .with(ivan())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomId":2,
                                 "startDate":"2026-12-01","endDate":null}"""))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getReservation_returns404ReservationNotFound() throws Exception {
        when(reservationService.getReservationById(999L))
                .thenThrow(new ReservationNotFoundException(999L));

        // GET тоже требует аутентификацию — добавляем .with(ivan())
        mockMvc.perform(get("/api/v1/reservations/999")
                        .with(ivan()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Reservation with id 999 not found"));
    }

    @Test
    void patchStatus_returns409OnInvalidTransition() throws Exception {
        when(reservationService.changeStatus(eq(1L), any(), eq("ivan@email.com")))
                .thenThrow(new InvalidStatusTransitionException(
                        ReservationStatus.CANCELLED, ReservationStatus.APPROVED));

        mockMvc.perform(patch("/api/v1/reservations/1/status")
                        .with(ivan())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void getStats_returnsJsonArray() throws Exception {
        when(reservationService.getStats(any(), any()))
                .thenReturn(List.of(new RoomStatsResponse(1L, "luxury", 3L)));

        mockMvc.perform(get("/api/v1/reservations/stats")
                        .with(ivan()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomName").value("luxury"))
                .andExpect(jsonPath("$[0].bookingCount").value(3));
    }

    @Test
    void requestWithoutUser_returns401or403() throws Exception {
        // БЕЗ .with(ivan()) — тест анонимного доступа
        mockMvc.perform(get("/api/v1/reservations"))
                .andExpect(status().is4xxClientError());
    }

    private static RequestPostProcessor ivan() {
        return SecurityMockMvcRequestPostProcessors.user("ivan@email.com").roles("USER");
    }
}