package grevcev.reservation.service;

import grevcev.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import grevcev.user.dto.UpdateUserRequest;
import grevcev.user.dto.UserResponse;
import grevcev.exception.UserNotFoundException;
import grevcev.user.model.User;
import grevcev.user.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void updateUser_success(){
        UpdateUserRequest updateUserRequest = new UpdateUserRequest("Ivan", "ivan@email.com");
        User user = User.builder().id(1L).name("Ivan").email("ivan26@email.com").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        UserResponse response = userService.update(1L, updateUserRequest);

        assertEquals(1L, response.id());
        assertEquals("Ivan", response.name());
        assertEquals("ivan@email.com", response.email());
    }

    @Test
    void updateUser_notFound(){
        UpdateUserRequest updateUserRequest = new UpdateUserRequest("Ivan", "ivan@email.com");

        when(userRepository.findById(3L)).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, ()-> userService.update(3L, updateUserRequest));
    }

    @Test
    void deleteUser_success(){

        User user = User.builder().id(1L).name("Ivan").email("ivan@email.com").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.delete(1L);

        verify(userRepository).delete(user);
    }

    @Test
    void deleteUser_notFound(){
        when(userRepository.findById(3L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, ()-> userService.delete(3L));
    }
}
