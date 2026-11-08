package grevcev.reservation.dbSeeder;

import grevcev.auth.security.LengthSafePasswordEncoder;
import grevcev.db.DataSeeder;
import grevcev.room.repository.RoomRepository;
import grevcev.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class DataSeederTest {
    @Test
    void existingUsersAreNotOverwrittenByDevSeeder() throws Exception {
        UserRepository users = mock(UserRepository.class);
        RoomRepository rooms = mock(RoomRepository.class);
        when(users.count()).thenReturn(1L);
        new DataSeeder(users, rooms, new LengthSafePasswordEncoder()).run();
        verify(users, never()).save(any());
        verifyNoInteractions(rooms);
    }
}
