package identity.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationMailTest {
    @Test
    void ofStatesTheTwoMinuteLimitAndEndsWithTheLink() {
        String link = "http://localhost:8080/auth/verify?token=abc";

        VerificationMail mail = VerificationMail.of(link, 120);

        assertEquals("HaloZalo - Verify your email", mail.getSubject());
        assertTrue(mail.getBody().startsWith(
            "Hãy xác nhận email của bạn bằng việc click vào đường link này trong thời hạn 2 phút:"
        ));
        assertTrue(mail.getBody().endsWith(link));
        assertFalse(mail.getBody().contains("password"));
    }

    @Test
    void ofRejectsAMissingLinkOrTime() {
        assertThrows(IllegalArgumentException.class, () -> VerificationMail.of(" ", 120));
        assertThrows(IllegalArgumentException.class, () -> VerificationMail.of("http://localhost/auth/verify?token=abc", 0));
    }
}
