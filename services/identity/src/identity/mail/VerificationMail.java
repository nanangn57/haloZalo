package identity.mail;

public final class VerificationMail {
    public static final String SUBJECT = "HaloZalo - Verify your email";

    private final String subject;
    private final String body;

    private VerificationMail(String subject, String body) {
        this.subject = subject;
        this.body = body;
    }

    public static VerificationMail of(String link, int ttlSeconds) {
        if (link == null || link.isBlank()) {
            throw new IllegalArgumentException("Verification link is required");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("Verification time is required");
        }
        String body = "Hãy xác nhận email của bạn bằng việc click vào đường link này trong thời hạn "
            + (ttlSeconds / 60)
            + " phút:\n"
            + link;
        return new VerificationMail(SUBJECT, body);
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }
}
