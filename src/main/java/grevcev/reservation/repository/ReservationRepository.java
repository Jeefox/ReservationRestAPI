package grevcev.reservation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import grevcev.room.dto.RoomStatsResponse;
import grevcev.reservation.model.Reservation;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    @Query("select r from Reservation r join fetch r.user join fetch r.room")
    List<Reservation> findAllWithAssociations();

    @Query(value = """
            select exists(select 1 from reservations r
            where r.room_id = :roomId and r.start_date < :endDate and r.end_date > :startDate
              and r.status in ('PENDING', 'APPROVED')
              and (:excludedId is null or r.id <> :excludedId))
            """, nativeQuery = true)
    boolean existsConflictingReservation(@Param("roomId") Long roomId,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate,
                                         @Param("excludedId") Long excludedId);

    @Override
    @EntityGraph(attributePaths = {"user", "room"})
    Page<Reservation> findAll(Specification<Reservation> spec, Pageable pageable);

    @Query("""
    select new grevcev.room.dto.RoomStatsResponse(
        r.room.id,
        r.room.name,
        count(r)
    )
    from Reservation r
    where (cast(:from as date) is null or r.endDate > :from)
      and (cast(:to as date) is null or r.startDate < :to)
      and r.status in (grevcev.reservation.ReservationStatus.PENDING, grevcev.reservation.ReservationStatus.APPROVED)
    group by r.room.id, r.room.name
    order by count(r) desc
    """)
    List<RoomStatsResponse> getStats(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
