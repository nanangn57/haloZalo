package identity.mail;

public final class PasswordResetMail {
    public static final String SUBJECT = "HaloZalo - Reset your password";

    private final String subject;
    private final String body;

    private PasswordResetMail(String subject, String body) {
        this.subject = subject;
        this.body = body;
    }

    public static PasswordResetMail of(String link, int ttlSeconds) {
        if (link == null || link.isBlank()) {
            throw new IllegalArgumentException("Reset link is required");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("Reset time is required");
        }
        String body = "Hãy đặt lại mật khẩu của bạn bằng việc click vào đường link này trong thời hạn "
            + (ttlSeconds / 60)
            + " phút:\n"
            + link;
        return new PasswordResetMail(SUBJECT, body);
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }
}
