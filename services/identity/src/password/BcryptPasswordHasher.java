package password;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Component;

@Component
public final class BcryptPasswordHasher implements PasswordHasher {
    @Override
    public String hash(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(10));
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, passwordHash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
