package grevcev.room.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateRoomRequest(
        @Size(min = 1, max = 100)@NotNull String name,
        @Positive(message = "Вместимость должна быть больше 0") Integer capacity) {
}
