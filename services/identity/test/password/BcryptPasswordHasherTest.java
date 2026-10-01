package password;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BcryptPasswordHasherTest {
    @Test
    void matchesOnlyTheOriginalPassword() {
        PasswordHasher hasher = new BcryptPasswordHasher();
        String hash = hasher.hash("password1");

        assertTrue(hasher.matches("password1", hash));
        assertFalse(hasher.matches("password2", hash));
        assertFalse(hasher.matches("password1", "not-a-hash"));
    }
}
