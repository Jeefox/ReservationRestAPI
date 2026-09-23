package grevcev.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotNull @Size(min = 2, max = 50)
        String name,
        @NotBlank @Email
        String email
) {
}
