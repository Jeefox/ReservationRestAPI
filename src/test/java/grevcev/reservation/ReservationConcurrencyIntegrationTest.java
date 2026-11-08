package grevcev.reservation;

import grevcev.AbstractIntegrationTest;
import grevcev.reservation.dto.CreateReservationRequest;
import grevcev.reservation.dto.UpdateReservationRequest;
import grevcev.reservation.dto.UpdateStatusRequest;
import grevcev.reservation.model.Reservation;
import grevcev.reservation.repository.ReservationRepository;
import grevcev.reservation.service.ReservationService;
import grevcev.room.model.Room;
import grevcev.room.repository.RoomRepository;
import grevcev.user.model.User;
import grevcev.user.model.UserRole;
import grevcev.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationConcurrencyIntegrationTest extends AbstractIntegrationTest {
    @Autowired private ReservationRepository reservations;
    @Autowired private RoomRepository rooms;
    @Autowired private UserRepository users;
    @Autowired private ReservationService service;
    @Autowired private PlatformTransactionManager transactions;

    @Test
    void staleApprovalCannotRestoreCancelledSlotAfterAnotherBooking() throws Exception {
        race(true);
    }

    @Test
    void stalePutCannotOverwriteCommittedPut() throws Exception {
        race(false);
    }

    private void race(boolean approval) throws Exception {
        LocalDate day = LocalDate.now().plusDays(60);
        User user = users.save(User.builder().name("Concurrent admin").email(UUID.randomUUID() + "@test.com")
                .password("unused").role(UserRole.ADMIN).build());
        Room room = rooms.save(Room.builder().name("Concurrent room").capacity(2).build());
        Long id = service.createReservation(new CreateReservationRequest(room.getId(), day, day.plusDays(2)),
                user.getEmail()).id();
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var stale = executor.submit(() -> {
                assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    reservations.findById(id).orElseThrow();
                    read.countDown();
                    try {
                        if (!proceed.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race timed out");
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                    if (approval) {
                        service.changeStatus(id, new UpdateStatusRequest(ReservationStatus.APPROVED), user.getEmail());
                    } else {
                        service.updateReservation(id, new UpdateReservationRequest(room.getId(), day.plusDays(6),
                                day.plusDays(8)), user.getEmail());
                    }
                })).isInstanceOf(ObjectOptimisticLockingFailureException.class);
            });
            assertThat(read.await(10, TimeUnit.SECONDS)).isTrue();
            if (approval) {
                service.changeStatus(id, new UpdateStatusRequest(ReservationStatus.CANCELLED), user.getEmail());
                service.createReservation(new CreateReservationRequest(room.getId(), day, day.plusDays(2)), user.getEmail());
            } else {
                service.updateReservation(id, new UpdateReservationRequest(room.getId(), day.plusDays(3), day.plusDays(5)),
                        user.getEmail());
            }
            proceed.countDown();
            stale.get(15, TimeUnit.SECONDS);
            Reservation result = reservations.findById(id).orElseThrow();
            assertThat(result.getStatus()).isEqualTo(approval ? ReservationStatus.CANCELLED : ReservationStatus.PENDING);
            assertThat(result.getStartDate()).isEqualTo(approval ? day : day.plusDays(3));
            assertThat(service.search(user.getId(), room.getId(), null, null, null,
                    org.springframework.data.domain.Pageable.unpaged(), user.getEmail()).getContent()
                    .stream().filter(r -> r.status() == ReservationStatus.PENDING || r.status() == ReservationStatus.APPROVED))
                    .hasSize(1);
        } finally {
            proceed.countDown();
            executor.shutdownNow();
        }
    }
}
