package grevcev.user.service;

import grevcev.user.dto.UpdateUserRequest;
import grevcev.user.dto.UserResponse;
import grevcev.user.model.ReservationUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import grevcev.exception.UserNotFoundException;
import grevcev.user.model.User;
import grevcev.user.repository.UserRepository;

import java.util.Collection;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly=true)
    public Page<UserResponse> findAll(Pageable pageable) {
        if (!isAdmin(getCurrentUser())) {
            throw new AccessDeniedException("Список пользователей доступен только администратору");
        }
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly=true)
    public UserResponse findById(Long id) {
        ReservationUserDetails currentUser = getCurrentUser();
        if (!isAdmin(currentUser) && !currentUser.getId().equals(id)) {
            throw new AccessDeniedException("Доступ к чужому профилю запрещён");
        }
        return toResponse(userRepository.findById(id).orElseThrow(()-> new UserNotFoundException(id)));
    }

    private UserResponse toResponse(User user){
        return new UserResponse(user.getId(),  user.getName(), user.getEmail());
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request){
        User user = userRepository.findById(id).orElseThrow(()-> new UserNotFoundException(id));

        ReservationUserDetails currentUser = getCurrentUser();

        Collection<? extends GrantedAuthority> authorities = currentUser.getAuthorities();

        boolean isAdmin = authorities.stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !currentUser.getId().equals(id)){
            throw new AccessDeniedException("User cannot update this profile");
        }
        user.setName(request.name());
        user.setEmail(request.email());

        return toResponse(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = userRepository.findById(id).orElseThrow(()-> new UserNotFoundException(id));
        userRepository.delete(user);
    }

    private boolean isAdmin(ReservationUserDetails user) {
        return user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private ReservationUserDetails getCurrentUser(){
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();
        return (ReservationUserDetails) authentication.getPrincipal();
    }
}
