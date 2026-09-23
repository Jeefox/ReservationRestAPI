package grevcev.db;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import grevcev.user.model.UserRole;
import grevcev.room.model.Room;
import grevcev.user.model.User;
import grevcev.room.repository.RoomRepository;
import grevcev.user.repository.UserRepository;

@Profile("dev")
@Component
public class DataSeeder implements CommandLineRunner {

    private static final String USER_PASSWORD_HASH = "$2a$10$he3s1K2JUz0DHagC7UVh/Oosq4u0L6kdcWpARyvnBTtPpEs1FzNDC";
    private static final String ADMIN_PASSWORD_HASH = "$2a$10$STtJk5kCZi1OVrro8ui3.u02HW.AVmEbs/c/1Y5H8Qt9FhahDSTk6";

    private UserRepository userRepository;
    private RoomRepository roomRepository;

    public DataSeeder(UserRepository userRepository, RoomRepository roomRepository) {
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
                .password(USER_PASSWORD_HASH)
                .role(UserRole.USER)
                .build());

        User maria = userRepository.save(User.builder()
                .name("Maria")
                .email("maria@email.com")
                .password(USER_PASSWORD_HASH)
                .role(UserRole.USER)
                .build());

        // Администратор — пароль admin1
        userRepository.save(User.builder()
                .name("Admin")
                .email("admin@admin.com")
                .password(ADMIN_PASSWORD_HASH)
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
