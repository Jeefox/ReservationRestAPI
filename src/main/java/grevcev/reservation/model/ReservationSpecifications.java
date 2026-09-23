package grevcev.reservation.model;

import org.springframework.data.jpa.domain.Specification;
import grevcev.reservation.ReservationStatus;

import java.time.LocalDate;

public class ReservationSpecifications {

    public static Specification<Reservation> hasUserId(Long userId) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Reservation> hasRoomId(Long roomId) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("room").get("id"), roomId);
    }

    public static Specification<Reservation> hasStatus(ReservationStatus status) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("status"), status);
    }

    public static Specification<Reservation> overlapsWith(LocalDate from, LocalDate to) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.and(
                        (criteriaBuilder.lessThan(root.get("startDate"), to)),
                        criteriaBuilder.greaterThan(root.get("endDate"), from));
    }
}
