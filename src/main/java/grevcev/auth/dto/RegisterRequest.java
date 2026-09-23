package grevcev.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 50)
        String name,
        @NotBlank @Email @Size(max = 100)
        String email,
        @NotBlank @Size(min = 8, message = "Пароль минимум 8 символов")
        @Pattern(regexp = ".*[A-Z].*", message = "Пароль должен содержать заглавную букву")
        @Pattern(regexp = ".*\\d.*", message = "Пароль должен содержать цифру")
        String password
) {
}
