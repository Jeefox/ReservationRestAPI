package grevcev.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import grevcev.auth.dto.AuthResponse;
import grevcev.auth.dto.LoginRequest;
import grevcev.auth.dto.RegisterRequest;
import grevcev.user.model.UserRole;
import grevcev.exception.EmailAlreadyExistsException;
import grevcev.exception.InvalidCredentialsException;
import grevcev.user.model.User;
import grevcev.user.repository.UserRepository;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }
    public AuthResponse register(RegisterRequest request) {
        if(userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        String hash = passwordEncoder.encode(request.password());
        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .role(UserRole.USER)
                .password(hash)
                .build();
        userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token);

    }

    public AuthResponse login(LoginRequest request) {
        Optional<User> maybeUser = userRepository.findByEmail(request.email());
        if (maybeUser.isEmpty() || !passwordEncoder.matches(request.password(), maybeUser.get().getPassword())) {
            throw new InvalidCredentialsException("Неверный email или пароль");
        }
        User user = maybeUser.get();
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token);
    }
}
