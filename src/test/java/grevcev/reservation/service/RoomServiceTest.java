package grevcev.reservation.service;

import grevcev.room.service.RoomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import grevcev.room.dto.RoomResponse;
import grevcev.room.dto.UpdateRoomRequest;
import grevcev.exception.RoomNotFoundException;
import grevcev.room.model.Room;
import grevcev.room.repository.RoomRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RoomServiceTest {
    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private RoomService roomService;

    @Test
    void updateRoom_success(){
        UpdateRoomRequest updateRoomRequest = new UpdateRoomRequest("Luxury", 3);
        Room room = Room.builder().id(1L).name("Luxury").capacity(2).build();

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        RoomResponse response = roomService.update(1L, updateRoomRequest);

        assertEquals(1L, response.id());
        assertEquals("Luxury", response.name());
        assertEquals(3, response.capacity());
    }

    @Test
    void updateRoom_notFound(){
        UpdateRoomRequest updateRoomRequest = new UpdateRoomRequest("Luxury", 3);

        when(roomRepository.findById(3L)).thenReturn(Optional.empty());
        assertThrows(RoomNotFoundException.class, ()-> roomService.update(3L, updateRoomRequest));
    }

    @Test
    void deleteRoom_success(){

        Room room = Room.builder().id(1L).name("Luxury").capacity(3).build();
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        roomService.delete(1L);

        verify(roomRepository).delete(room);
    }

    @Test
    void deleteRoom_notFound(){
        when(roomRepository.findById(3L)).thenReturn(Optional.empty());

        assertThrows(RoomNotFoundException.class, ()-> roomService.delete(3L));
    }
}
