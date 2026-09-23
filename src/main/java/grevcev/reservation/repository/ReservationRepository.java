package grevcev.reservation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import grevcev.reservation.ReservationStatus;
import grevcev.room.dto.RoomStatsResponse;
import grevcev.reservation.model.Reservation;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    @Query("select r from Reservation r join fetch r.user join fetch r.room")
    List<Reservation> findAllWithAssociations();

    @Query("select r from Reservation r where r.room.id = :roomId and r.startDate<:endDate and :startDate< r.endDate " +
            "and r.status <> :cancelledStatus and (:excludedId is null or r.id <> :excludedId)")
    List<Reservation> findConflictingReservations(@Param("roomId")Long roomId, @Param("startDate") LocalDate startDate,
                                                @Param("endDate")LocalDate endDate,
                                                @Param("cancelledStatus") ReservationStatus cancelledStatus,
                                                @Param("excludedId") Long excludedId);

    @EntityGraph(attributePaths = {"user", "room"})
    Page<Reservation> findAllBy(Specification<Reservation> spec, Pageable pageable);

    @Query("""
    select new grevcev.room.dto.RoomStatsResponse(
        r.room.id,
        r.room.name,
        count(r)
    )
    from Reservation r
    where (:from is null or r.endDate >= :from)
      and (:to is null or r.startDate <= :to)
    group by r.room.id, r.room.name
    order by count(r) desc
    """)
    List<RoomStatsResponse> getStats(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
