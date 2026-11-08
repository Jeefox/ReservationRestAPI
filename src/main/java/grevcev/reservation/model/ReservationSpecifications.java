package grevcev.reservation.model;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import grevcev.reservation.ReservationStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (to != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("startDate"), to));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThan(root.get("endDate"), from));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
