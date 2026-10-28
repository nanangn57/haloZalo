package identity.mail;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmtpMailSenderTest {
    @Test
    void sendPutsTheVerificationLinkInAPlainMessage() throws Exception {
        RecordingMail mail = new RecordingMail();
        SmtpMailSender sender = new SmtpMailSender(mail, "noreply@halozalo.local");
        String link = "http://localhost:8080/auth/verify?token=abc";

        sender.send("anh@example.com", "Verify your email", link);

        MimeMessage message = mail.sent.get(0);
        assertEquals("noreply@halozalo.local", message.getFrom()[0].toString());
        assertEquals("anh@example.com", message.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("Verify your email", message.getSubject());
        assertEquals(link, message.getContent().toString());
        assertFalse(message.getContent().toString().contains("password"));
    }

    @Test
    void sendLeavesTheTransportErrorForTheCaller() {
        RecordingMail mail = new RecordingMail();
        mail.failNext = true;
        SmtpMailSender sender = new SmtpMailSender(mail, "noreply@halozalo.local");

        assertThrows(MailSendException.class, () ->
            sender.send("anh@example.com", "Verify your email", "http://localhost:8080/auth/verify?token=abc")
        );
        assertEquals(0, mail.sent.size());
    }

    @Test
    void sendWrapsAnAddressTheMessageCannotCarry() {
        RecordingMail mail = new RecordingMail();
        SmtpMailSender sender = new SmtpMailSender(mail, "noreply@halozalo.local");

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
            sender.send("not an email", "Verify your email", "http://localhost:8080/auth/verify?token=abc")
        );

        assertEquals("Could not send mail", error.getMessage());
        assertEquals(0, mail.sent.size());
    }

    private static final class RecordingMail implements JavaMailSender {
        private final List<MimeMessage> sent = new ArrayList<>();
        private boolean failNext;

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessage mimeMessage) {
            if (failNext) {
                throw new MailSendException("smtp down");
            }
            sent.add(mimeMessage);
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            throw new UnsupportedOperationException();
        }
    }
}
