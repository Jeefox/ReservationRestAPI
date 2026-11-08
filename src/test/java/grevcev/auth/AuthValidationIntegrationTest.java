package grevcev.auth;

import com.jayway.jsonpath.JsonPath;
import grevcev.AbstractIntegrationTest;
import grevcev.user.model.User;
import grevcev.user.model.UserRole;
import grevcev.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Интеграционные тесты валидации Auth и Dev-аккаунтов (Issue #7)")
public class AuthValidationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final String testEmail = "validation-test@test.com";

    @BeforeEach
    void setUp() throws Exception {
        ensureDevAccounts();
        // Гарантируем, что у нас есть пользователь с валидным паролем минимальной длины для тестов логина.
        // Не повторяем регистрацию: контекст и база данных общие для методов этого integration test.
        if (userRepository.findByEmail(testEmail).isEmpty()) {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                            {
                                "name": "Validation User",
                                "email": "%s",
                                "password": "Passw0rd"
                            }
                            """.formatted(testEmail)))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    private void ensureDevAccounts() {
        userRepository.findByEmail("admin@admin.com").orElseGet(() -> userRepository.save(User.builder()
                .name("Admin")
                .email("admin@admin.com")
                .password(passwordEncoder.encode("Admin123"))
                .role(UserRole.ADMIN)
                .build()));

        userRepository.findByEmail("ivan@email.com").orElseGet(() -> userRepository.save(User.builder()
                .name("Ivan")
                .email("ivan@email.com")
                .password(passwordEncoder.encode("password1"))
                .role(UserRole.USER)
                .build()));
    }

    // ==========================================
    // 1. Тесты граничных значений для Регистрации
    // ==========================================

    @Test
    @DisplayName("Регистрация: пароль 7 символов -> 400 Bad Request")
    void register_passwordTooShort_returns400() throws Exception {
        // Pasw0rd = 7 символов (есть заглавная и цифра, чтобы упасть именно по @Size, а не по @Pattern)
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Test User",
                                    "email": "short@test.com",
                                    "password": "Pasw0rd"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.password").value("Пароль должен содержать от 8 до 100 символов"));
    }

    @Test
    @DisplayName("Регистрация: пароль 8 символов (минимум) -> 200 OK")
    void register_passwordMinLength_returnsSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Test User",
                                    "email": "min-length@test.com",
                                    "password": "Passw0rd"
                                }
                                """))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("Регистрация: пароль 100 символов (максимум) -> 200 OK")
    void register_passwordMaxLength_returnsSuccess() throws Exception {
        String valid100CharPassword = generateValidPassword(100);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Test User",
                                    "email": "max-length@test.com",
                                    "password": "%s"
                                }
                                """.formatted(valid100CharPassword)))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("Регистрация: пароль 101 символ -> 400 Bad Request")
    void register_passwordTooLong_returns400() throws Exception {
        String invalid101CharPassword = generateValidPassword(101);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Test User",
                                    "email": "too-long@test.com",
                                    "password": "%s"
                                }
                                """.formatted(invalid101CharPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    // ==========================================
    // 2. Тесты граничных значений для Логина
    // ==========================================

    @Test
    @DisplayName("Логин: пароль 7 символов -> 400 Bad Request (валидация DTO)")
    void login_passwordTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "Pasw0rd"
                                }
                                """.formatted(testEmail)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Логин: пароль 8 символов -> 200 OK + JWT")
    void login_passwordMinLength_returnsSuccess() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "Passw0rd"
                                }
                                """.formatted(testEmail)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(response, "$.token")).isNotBlank();
    }

    @Test
    @DisplayName("Логин: пароль 100 символов -> 200 OK + JWT")
    void login_passwordMaxLength_returnsSuccess() throws Exception {
        // Сначала регистрируем пользователя со 100-символьным паролем
        String valid100CharPassword = generateValidPassword(100);
        String hundredCharEmail = "hundred@test.com";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Test User",
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(hundredCharEmail, valid100CharPassword)))
                .andExpect(status().is2xxSuccessful());

        // Теперь логинимся с ним
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(hundredCharEmail, valid100CharPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(response, "$.token")).isNotBlank();
    }

    @Test
    @DisplayName("Логин: пароль 101 символ -> 400 Bad Request (валидация DTO)")
    void login_passwordTooLong_returns400() throws Exception {
        String invalid101CharPassword = generateValidPassword(101);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(testEmail, invalid101CharPassword)))
                .andExpect(status().isBadRequest());
    }

    // ==========================================
    // 3. Тесты Dev-аккаунтов
    // ==========================================

    @Test
    @DisplayName("Dev-администратор может успешно войти в систему")
    void devAdminCanLogin() throws Exception {
        // ⚠️ ЗАМЕНИТЕ эти данные на реальные из вашего DataSeeder / application-test.yml
        String devAdminEmail = "admin@admin.com";
        String devAdminPassword = "Admin123";

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(devAdminEmail, devAdminPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(response, "$.token");
        assertThat(token).isNotBlank();

        // Опционально: можно декодировать JWT и проверить, что роль действительно ADMIN
    }

    @Test
    @DisplayName("Dev-пользователь может успешно войти в систему")
    void devUserCanLogin() throws Exception {
        // ⚠️ ЗАМЕНИТЕ эти данные на реальные из вашего DataSeeder
        String devUserEmail = "ivan@email.com";
        String devUserPassword = "password1";

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(devUserEmail, devUserPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(response, "$.token");
        assertThat(token).isNotBlank();
    }

    // ==========================================
    // Вспомогательные методы
    // ==========================================

    /**
     * Генерирует валидный пароль заданной длины, который удовлетворяет всем @Pattern:
     * - Содержит заглавную букву (начинается с 'P')
     * - Содержит цифру (содержит '0')
     * - Имеет точную длину length
     */
    private String generateValidPassword(int length) {
        if (length < 8) {
            throw new IllegalArgumentException("Длина пароля должна быть не менее 8 для соответствия @Pattern");
        }
        // Формат: 'P' + 'a' * (length - 4) + '0rd'.
        return "P" + "a".repeat(length - 4) + "0rd";
    }
}
