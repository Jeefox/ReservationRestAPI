package grevcev.reservation.dbSeeder;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class DataSeederTest {
    @Test
    void generatePasswordHash() {
        String hash = new BCryptPasswordEncoder().encode("password1");
        System.out.println("HASH: " + hash);
    }
}
