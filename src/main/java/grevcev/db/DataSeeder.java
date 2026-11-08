package grevcev.db;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
import grevcev.user.model.UserRole;
import grevcev.room.model.Room;
import grevcev.user.model.User;
import grevcev.room.repository.RoomRepository;
import grevcev.user.repository.UserRepository;

@Profile("dev")
@Component
public class DataSeeder implements CommandLineRunner {

    private UserRepository userRepository;
    private RoomRepository roomRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, RoomRepository roomRepository, PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
    }
    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            return;
        }

        // Обычные пользователи — пароль password1
        User ivan = userRepository.save(User.builder()
                .name("Ivan")
                .email("ivan@email.com")
                .password(passwordEncoder.encode("password1"))
                .role(UserRole.USER)
                .build());

        User maria = userRepository.save(User.builder()
                .name("Maria")
                .email("maria@email.com")
                .password(passwordEncoder.encode("password1"))
                .role(UserRole.USER)
                .build());

        // Администратор — пароль Admin123
        userRepository.save(User.builder()
                .name("Admin")
                .email("admin@admin.com")
                .password(passwordEncoder.encode("Admin123"))
                .role(UserRole.ADMIN)
                .build());

        // Комнаты
        Room luxuryRoom = roomRepository.save(Room.builder()
                .name("luxury")
                .capacity(2)
                .build());

        Room casualRoom = roomRepository.save(Room.builder()
                .name("casual")
                .capacity(4)
                .build());
    }
}
