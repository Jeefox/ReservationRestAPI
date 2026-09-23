package grevcev.kafka.event;

import java.time.LocalDate;

public record ReservationCreatedKafkaEvent(
        Long reservationId,
        Long userId,
        Long roomId,
        LocalDate startDate,
        LocalDate endDate
) {
}
