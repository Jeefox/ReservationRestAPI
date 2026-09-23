package grevcev.room.dto;

public record RoomStatsResponse(
        Long roomId,
        String roomName,
        Long bookingCount
) {
}
