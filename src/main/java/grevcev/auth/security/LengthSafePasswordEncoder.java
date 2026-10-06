package grevcev.auth.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Keeps compatibility with the existing BCrypt hashes while handling passwords
 * longer than BCrypt's 72-byte input limit.
 */
public final class LengthSafePasswordEncoder implements PasswordEncoder {

    private static final int BCRYPT_MAX_INPUT_BYTES = 72;

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        return bcrypt.encode(normalize(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (isTooLong(rawPassword)) {
            return bcrypt.matches(normalize(rawPassword), encodedPassword);
        }
        return bcrypt.matches(rawPassword, encodedPassword);
    }

    private CharSequence normalize(CharSequence rawPassword) {
        if (!isTooLong(rawPassword)) {
            return rawPassword;
        }

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private boolean isTooLong(CharSequence rawPassword) {
        return rawPassword.toString().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_INPUT_BYTES;
    }
}
