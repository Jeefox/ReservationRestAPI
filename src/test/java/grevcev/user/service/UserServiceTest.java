package grevcev.user.service;

import grevcev.exception.UserNotFoundException;
import grevcev.user.dto.UpdateUserRequest;
import grevcev.user.dto.UserResponse;
import grevcev.user.model.ReservationUserDetails;
import grevcev.user.model.User;
import grevcev.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void updateUser_success(){

        ReservationUserDetails userDetails = new ReservationUserDetails(
                1L, "user@test.com", "USER", List.of(() -> "ROLE_USER")
        );
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        UpdateUserRequest updateUserRequest = new UpdateUserRequest("Ivan", "ivan@email.com");
        User user = User.builder().id(1L).name("OldName").email("old@email.com").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.update(1L, updateUserRequest);

        assertEquals(1L, response.id());
        assertEquals("Ivan", response.name());
        assertEquals("ivan@email.com", response.email());
      
        SecurityContextHolder.clearContext();
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
