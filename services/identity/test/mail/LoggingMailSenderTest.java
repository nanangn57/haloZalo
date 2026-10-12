package mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingMailSenderTest {
    @Test
    void lineCarriesRecipientAndBody() {
        String line = LoggingMailSender.line(
            "anh@example.com",
            "Verify your email",
            "http://localhost:8080/auth/verify?token=abc"
        );

        assertTrue(line.contains("to=anh@example.com"));
        assertTrue(line.contains("subject=Verify your email"));
        assertTrue(line.contains("body=http://localhost:8080/auth/verify?token=abc"));
        assertFalse(line.contains("password"));
    }
}
