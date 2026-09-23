package grevcev.reservation.model;

import grevcev.room.model.Room;
import grevcev.user.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import grevcev.reservation.ReservationStatus;

import java.time.LocalDate;

@Entity
@Table(name="reservations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {
        @Id
        @GeneratedValue(strategy= GenerationType.IDENTITY)
        private Long id;
        @NotNull
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "user_id")
        private User user;
        @NotNull
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "room_id")
        private Room room;
        @NotNull
        @FutureOrPresent
        private LocalDate startDate;
        @NotNull
        private LocalDate endDate;
        @NotNull
        @Enumerated(EnumType.STRING)
        ReservationStatus status;

        @AssertTrue
        public boolean isDateValid(){
                if(startDate ==null || endDate ==null) return true;
                return !endDate.isBefore(startDate);
        }
}
