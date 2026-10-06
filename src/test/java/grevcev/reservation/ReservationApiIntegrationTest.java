package grevcev.reservation;

import com.jayway.jsonpath.JsonPath;
import grevcev.AbstractIntegrationTest;
import grevcev.notification.repository.NotificationRepository;
import grevcev.reservation.repository.ReservationRepository;
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
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import grevcev.reservation.model.Reservation;
import grevcev.notification.model.NotificationEntity;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.awaitility.Awaitility.await;
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

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

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
    // ============== Issue #5: Soft Delete Tests ==============

    // ============== Issue #5: Soft Delete Tests ==============

    @Test
    void softDelete_preservesReservationInDbWithDeletedStatus() throws Exception {
        String email = "owner_sd1_" + System.currentTimeMillis() + "@test.com";
        registerUser("Owner", email, "Password123");
        String token = login(email, "Password123");
        String adminToken = login("integ-admin@test.com", "password1");

        long roomId = createRoomAsAdmin(adminToken);

        MockHttpServletResponse reservationResponse = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\": " + roomId + ", \"startDate\": \"2027-05-01\", \"endDate\": \"2027-05-05\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse();

        // 👇 ИСПРАВЛЕНО: безопасное приведение типов 👇
        Long reservationId = ((Number) JsonPath.read(reservationResponse.getContentAsString(), "$.id")).longValue();

        mockMvc.perform(delete("/api/v1/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        Reservation deleted = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(deleted.getStatus()).isEqualTo(ReservationStatus.DELETED);
    }

    @Test
    void softDelete_allowsNotificationFkToRemainValid() throws Exception {
        String email = "owner_sd2_" + System.currentTimeMillis() + "@test.com";
        registerUser("Owner2", email, "Password123");
        String token = login(email, "Password123");
        String adminToken = login("integ-admin@test.com", "password1");

        long roomId = createRoomAsAdmin(adminToken);

        MockHttpServletResponse reservationResponse = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\": " + roomId + ", \"startDate\": \"2027-06-01\", \"endDate\": \"2027-06-05\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse();

        Long reservationId = ((Number) JsonPath.read(reservationResponse.getContentAsString(), "$.id")).longValue();

        // 1. Коммитим, чтобы Kafka-консьюмер (в другом потоке) увидел эту бронь и создал уведомление
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // 2. Ждем, пока консьюмер отработает
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> !findNotificationsByReservationId(reservationId).isEmpty());

        List<NotificationEntity> notifications = findNotificationsByReservationId(reservationId);
        assertThat(notifications).hasSize(1);
        UUID notificationId = notifications.get(0).getId();

        // 3. Выполняем Soft Delete
        mockMvc.perform(delete("/api/v1/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 4. ASSERTIONS (Проверки)

        // Проверяем, что уведомление существует (FK валиден, запись не упала)
        NotificationEntity notification = notificationRepository.findById(notificationId).orElseThrow();
        assertThat(notification).isNotNull();

        // 👇 ИСПРАВЛЕНО: Запрашиваем бронь напрямую, избегая LazyInitializationException
        Reservation deletedReservation = reservationRepository.findById(reservationId).orElseThrow();

        assertThat(deletedReservation.getStatus()).isEqualTo(ReservationStatus.DELETED);
    }

    @Test
    void kafkaConsumer_skipsNotificationForDeletedReservation() throws Exception {
        String email = "owner_sd3_" + System.currentTimeMillis() + "@test.com";
        registerUser("Owner3", email, "Password123");
        String token = login(email, "Password123");
        String adminToken = login("integ-admin@test.com", "password1");

        long roomId = createRoomAsAdmin(adminToken);

        MockHttpServletResponse reservationResponse = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\": " + roomId + ", \"startDate\": \"2027-07-01\", \"endDate\": \"2027-07-05\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse();

        Long reservationId = ((Number) JsonPath.read(reservationResponse.getContentAsString(), "$.id")).longValue();

        mockMvc.perform(delete("/api/v1/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        await()
                .during(2, TimeUnit.SECONDS)
                .atMost(3, TimeUnit.SECONDS)
                .until(() -> true);

        List<NotificationEntity> notifications = findNotificationsByReservationId(reservationId);
        assertThat(notifications).isEmpty();
    }

    @Test
    void softDelete_notOwner_throws403() throws Exception {
        String ownerEmail = "owner_sd4_" + System.currentTimeMillis() + "@test.com";
        registerUser("Owner4", ownerEmail, "Password123");
        String ownerToken = login(ownerEmail, "Password123");
        String adminToken = login("integ-admin@test.com", "password1");

        long roomId = createRoomAsAdmin(adminToken);

        MockHttpServletResponse reservationResponse = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\": " + roomId + ", \"startDate\": \"2027-08-01\", \"endDate\": \"2027-08-05\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse();

        // 👇 ИСПРАВЛЕНО (именно здесь падал тест) 👇
        Long reservationId = ((Number) JsonPath.read(reservationResponse.getContentAsString(), "$.id")).longValue();

        String attackerEmail = "attacker_sd4_" + System.currentTimeMillis() + "@test.com";
        registerUser("Attacker4", attackerEmail, "Password123");
        String attackerToken = login(attackerEmail, "Password123");

        mockMvc.perform(delete("/api/v1/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden());

        Reservation unchanged = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(unchanged.getStatus()).isNotEqualTo(ReservationStatus.DELETED);
    }

    // Вспомогательный метод (остается без изменений, так как reservationId — это Long)
    private List<NotificationEntity> findNotificationsByReservationId(Long reservationId) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getReservation() != null
                        && n.getReservation().getId().equals(reservationId))
                .toList();
    }

    // Вспомогательный метод для создания комнаты админом
    private Long createRoomAsAdmin(String adminToken) throws Exception {
        MockHttpServletResponse roomResponse = mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test-room\",\"capacity\":2}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse();

        // Явно приводим к Number и вызываем longValue() для безопасности
        return ((Number) JsonPath.read(roomResponse.getContentAsString(), "$.id")).longValue();
    }
 }
