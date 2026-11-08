package grevcev.room.dto;

public record RoomResponse(
        Long id,
        String name,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Справочная вместимость; бронь занимает комнату целиком")
        int capacity
) {
}
