package analytics.query;

public final class VerifyFunnel {
    private final long registered;
    private final long verified;
    private final long firstMessage;

    public VerifyFunnel(long registered, long verified, long firstMessage) {
        this.registered = registered;
        this.verified = verified;
        this.firstMessage = firstMessage;
    }

    public long getRegistered() {
        return registered;
    }

    public long getVerified() {
        return verified;
    }

    public long getFirstMessage() {
        return firstMessage;
    }
}
