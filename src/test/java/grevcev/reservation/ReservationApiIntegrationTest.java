package grevcev.reservation;

import com.jayway.jsonpath.JsonPath;
import grevcev.AbstractIntegrationTest;
import grevcev.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;


import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional // Откатывает все изменения БД после каждого теста
@Sql(statements = {
        "DELETE FROM users WHERE email = 'integ-admin@test.com'", // <-- Очищаем перед вставкой
        "INSERT INTO users (name, email, password, role) VALUES ('IntegAdmin', 'integ-admin@test.com', '$2a$10$he3s1K2JUz0DHagC7UVh/Oosq4u0L6kdcWpARyvnBTtPpEs1FzNDC', 'ADMIN')"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ReservationApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // Внедряем репозиторий для проверки реального состояния БД (side effects)
    @Autowired
    private UserRepository userRepository;

    @Test
    void fullBookingFlow_withRolesAndConflict() throws Exception {
        // 1. Админ (создан через @Sql, пароль password1) логинится
        String adminToken = login("integ-admin@test.com", "password1");

        // 2. Админ создает комнату — 201
        MockHttpServletResponse roomResponse = mockMvc.perform(
                        post("/api/v1/rooms")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"integ-room\",\"capacity\":2}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();

        Number roomIdValue = JsonPath.read(
                roomResponse.getContentAsString(),
                "$.id"
        );

        long roomId = roomIdValue.longValue();

        // 3. Регистрация обычного юзера (открытый эндпоинт)
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ivan\",\"email\":\"ivan@test.com\",\"password\":\"Password123\"}"))
                .andExpect(status().is2xxSuccessful());

        // 4. Логин обычного юзера
        String userToken = login("ivan@test.com", "Password123");

        // 5. Обычный юзер НЕ может создать комнату — 403
        mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"hack-room\",\"capacity\":1}"))
                .andExpect(status().isForbidden());

        // 6. Юзер создает бронь в созданной комнате — 201
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "roomId": %d,
                            "startDate": "2027-01-10",
                            "endDate": "2027-01-15"
                        }
                        """.formatted(roomId)))
                .andExpect(status().isCreated());

        // 7. Пересекающаяся бронь — 409
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "roomId": %d,
                            "startDate": "2027-01-14",
                            "endDate": "2027-01-20"
                        }
                        """.formatted(roomId)))
                .andExpect(status().isConflict());

        // 8. Аноним не может смотреть брони — 4xx
        mockMvc.perform(get("/api/v1/reservations"))
                .andExpect(status().is4xxClientError());
    }

    private String login(String email, String password) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        return JsonPath.read(response.getContentAsString(), "$.token");
    }

    @Test
    void userCanUpdateOwnProfile_returns200AndUpdatesDb() throws Exception {
        // Arrange
        Long userId = registerUser("OwnUser", "own@test.com", "Password123");
        String token = login("own@test.com", "Password123");

        // Act
        mockMvc.perform(put("/api/v1/users/" + userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"UpdatedOwnName\",\"email\":\"updated_own@test.com\"}"))
                .andExpect(status().isOk());

        // Assert: проверяем, что данные в БД действительно изменились
        var updatedUser = userRepository.findById(userId).orElseThrow();
        assertThat(updatedUser.getName()).isEqualTo("UpdatedOwnName");
        assertThat(updatedUser.getEmail()).isEqualTo("updated_own@test.com");
    }

    @Test
    void userCannotUpdateAnotherProfile_returns403AndDbRemainsUnchanged() throws Exception {
        // Arrange
        Long targetUserId = registerUser("TargetUser", "target@test.com", "Password123");
        Long attackerUserId = registerUser("AttackerUser", "attacker@test.com", "Password123");
        String attackerToken = login("attacker@test.com", "Password123");

        // Запоминаем исходное состояние жертвы
        var originalTargetUser = userRepository.findById(targetUserId).orElseThrow();
        String originalName = originalTargetUser.getName();

        // Act: пытаемся взломать
        mockMvc.perform(put("/api/v1/users/" + targetUserId)
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"HackedName\",\"email\":\"hacked@test.com\"}"))
                .andExpect(status().isForbidden()); // 403

        // Assert: КРИТИЧЕСКИ ВАЖНО — проверяем, что БД НЕ изменилась
        var unchangedTargetUser = userRepository.findById(targetUserId).orElseThrow();
        assertThat(unchangedTargetUser.getName())
                .as("Имя пользователя не должно было измениться после попытки взлома")
                .isEqualTo(originalName);
    }

    @Test
    void adminCanUpdateAnotherUserProfile_returns200AndUpdatesDb() throws Exception {
        // Arrange
        Long targetUserId = registerUser("TargetForAdmin", "target_admin@test.com", "Password123");
        // Используем админа, созданного через @Sql
        String adminToken = login("integ-admin@test.com", "password1");

        // Act
        mockMvc.perform(put("/api/v1/users/" + targetUserId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"AdminUpdatedName\",\"email\":\"admin_updated@test.com\"}"))
                .andExpect(status().isOk());

        // Assert: проверяем, что админ реально изменил данные
        var updatedUser = userRepository.findById(targetUserId).orElseThrow();
        assertThat(updatedUser.getName()).isEqualTo("AdminUpdatedName");
        assertThat(updatedUser.getEmail()).isEqualTo("admin_updated@test.com");
    }

    @Test
    void anonymousCannotUpdateProfile_returns403() throws Exception {
        // Arrange
        Long targetUserId = registerUser("TargetAnon", "target_anon@test.com", "Password123");

        // Act: запрос БЕЗ заголовка Authorization
        mockMvc.perform(put("/api/v1/users/" + targetUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"AnonUpdated\",\"email\":\"anon@test.com\"}"))
                .andExpect(status().isForbidden()); // 401
    }

    /**
     * Регистрирует пользователя и возвращает его ID из базы данных.
     * Это делает тесты чище и избавляет от дублирования кода регистрации.
     */
    private Long registerUser(String name, String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is2xxSuccessful()); // или isCreated(), зависит от вашей реализации

        // Получаем ID из БД, чтобы быть на 100% уверенными в корректности данных для тестов
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found after registration"))
                .getId();
    }

    @Test
    void userCannotGetAnotherReservation_returns403() throws Exception {

        registerUser("ReservationOwner", "reservation_owner@test.com", "Password123");
        registerUser("OtherUser", "reservation_other@test.com", "Password123");

        String ownerToken = login("reservation_owner@test.com", "Password123");
        String otherUserToken = login("reservation_other@test.com", "Password123");

        // Создаём комнату админом
        String adminToken = login("integ-admin@test.com", "password1");

        MockHttpServletResponse roomResponse = mockMvc.perform(
                        post("/api/v1/rooms")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"reservation-security-room\",\"capacity\":2}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();

        long roomId = ((Number) JsonPath.read(
                roomResponse.getContentAsString(), "$.id")).longValue();

        // Создаём бронь владельцем
        MockHttpServletResponse reservationResponse = mockMvc.perform(
                        post("/api/v1/reservations")
                                .header("Authorization", "Bearer " + ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "roomId": %d,
                                        "startDate": "2027-02-10",
                                        "endDate": "2027-02-15"
                                    }
                                    """.formatted(roomId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();

        long reservationId = ((Number) JsonPath.read(
                reservationResponse.getContentAsString(), "$.id")).longValue();

        // OtherUser пытается посмотреть чужую бронь
        mockMvc.perform(get("/api/v1/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCannotChangeAnotherReservationStatus_returns403BeforeTransitionCheck() throws Exception {
        // Arrange
        registerUser("StatusOwner", "status_owner@test.com", "Password123");
        registerUser("StatusOther", "status_other@test.com", "Password123");

        String ownerToken = login("status_owner@test.com", "Password123");
        String otherUserToken = login("status_other@test.com", "Password123");
        String adminToken = login("integ-admin@test.com", "password1");

        // Создаём комнату
        MockHttpServletResponse roomResponse = mockMvc.perform(
                        post("/api/v1/rooms")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"status-security-room\",\"capacity\":2}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();

        long roomId = ((Number) JsonPath.read(
                roomResponse.getContentAsString(), "$.id")).longValue();

        // Создаём бронь владельцем
        MockHttpServletResponse reservationResponse = mockMvc.perform(
                        post("/api/v1/reservations")
                                .header("Authorization", "Bearer " + ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "roomId": %d,
                                        "startDate": "2027-03-10",
                                        "endDate": "2027-03-15"
                                    }
                                    """.formatted(roomId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();

        long reservationId = ((Number) JsonPath.read(
                reservationResponse.getContentAsString(), "$.id")).longValue();

        // Владелец отменяет бронь
        mockMvc.perform(patch("/api/v1/reservations/" + reservationId + "/status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {
                    "status": "CANCELLED"
                }
                """))
                .andExpect(status().isOk());

        // Чужой USER пытается сделать CANCELLED → APPROVED.
        // Должен получить 403 ДО проверки FSM.
        mockMvc.perform(patch("/api/v1/reservations/" + reservationId + "/status")
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {
                    "status": "APPROVED"
                }
                """))
                .andExpect(status().isForbidden());
    }
}