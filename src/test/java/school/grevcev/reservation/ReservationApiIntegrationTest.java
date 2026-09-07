package school.grevcev.reservation;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")   // ← ВАЖНО: используем H2 вместо Testcontainers
@Sql(statements = "INSERT INTO users (name, email, password, role) VALUES ('IntegAdmin', 'integ-admin@test.com', '$2a$10$he3s1K2JUz0DHagC7UVh/Oosq4u0L6kdcWpARyvnBTtPpEs1FzNDC', 'ADMIN')",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ReservationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullBookingFlow_withRolesAndConflict() throws Exception {
        // 1. Админ (создан через @Sql, пароль password1) логинится
        String adminToken = login("integ-admin@test.com", "password1");

        // 2. Админ создает комнату — 201
        mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"integ-room\",\"capacity\":2}"))
                .andExpect(status().isCreated());

        // 3. Регистрация обычного юзера (открытый эндпоинт)
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ivan\",\"email\":\"ivan@test.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());

        // 4. Логин обычного юзера
        String userToken = login("ivan@test.com", "secret123");

        // 5. Обычный юзер НЕ может создать комнату — 403
        mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"hack-room\",\"capacity\":1}"))
                .andExpect(status().isForbidden());

        // 6. Юзер создает бронь в созданной комнате (id=1) — 201
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"startDate\":\"2027-01-10\",\"endDate\":\"2027-01-15\"}"))
                .andExpect(status().isCreated());

        // 7. Пересекающаяся бронь — 409
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"startDate\":\"2027-01-14\",\"endDate\":\"2027-01-20\"}"))
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
}