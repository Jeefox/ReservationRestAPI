package grevcev.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Size(min = 2, max = 50)
        String name,
        @NotBlank @Email @Size(max = 100)
        String email) {
}
