package grevcev.user.service;

import grevcev.user.dto.CreateUserRequest;
import grevcev.user.dto.UpdateUserRequest;
import grevcev.user.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import grevcev.exception.UserNotFoundException;
import grevcev.user.model.User;
import grevcev.user.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly=true)
    public Page<UserResponse> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly=true)
    public UserResponse findById(Long id) {
        return toResponse(userRepository.findById(id).orElseThrow(()-> new UserNotFoundException(id)));
    }

    @Transactional
    public UserResponse save(CreateUserRequest request) {
        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .build();
        return toResponse(userRepository.save(user));
    }

    private UserResponse toResponse(User user){
        return new UserResponse(user.getId(),  user.getName(), user.getEmail());
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id).orElseThrow(()-> new UserNotFoundException(id));
        user.setName(request.name());
        user.setEmail(request.email());
        return toResponse(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = userRepository.findById(id).orElseThrow(()-> new UserNotFoundException(id));
        userRepository.delete(user);
    }
}
