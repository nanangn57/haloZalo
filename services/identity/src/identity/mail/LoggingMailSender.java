package identity.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "identity.mail.mode", havingValue = "log", matchIfMissing = true)
public final class LoggingMailSender implements MailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info(line(to, subject, body));
    }

    static String line(String to, String subject, String body) {
        return "to=" + to + " subject=" + subject + " body=" + body;
    }
}
