package grevcev.auth.service;

import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class JwtServiceTest {

    @Test
    public void invalidSignatureShouldBeRejected(){
        JwtService validService = new JwtService(
                "valid-secret-key-for-jwt-signing-must-be-at-least-256-bits-long-0123456789",
                3600000
        );

        JwtService anotherService = new JwtService(
                "another-secret-key-for-jwt-signing-must-be-at-least-256-bits-long-0123456789",
                3600000
        );

        String token = validService.generateToken("test@test.com", "USER");

        assertThrows(SignatureException.class, () -> anotherService.parseToken(token));
    }
}
