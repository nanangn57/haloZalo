package identity.auth;

public final class RegisterAccepted {
    public static final String STATUS_PENDING_VERIFICATION = "PENDING_VERIFICATION";
    public static final String STATUS_ACTIVE = "ACTIVE";

    private final String email;
    private final String status;

    public RegisterAccepted(String email, String status) {
        this.email = email;
        this.status = status;
    }

    public static RegisterAccepted pendingVerification(String email) {
        return new RegisterAccepted(email, STATUS_PENDING_VERIFICATION);
    }

    public static RegisterAccepted active(String email) {
        return new RegisterAccepted(email, STATUS_ACTIVE);
    }

    public String getEmail() {
        return email;
    }

    public String getStatus() {
        return status;
    }
}
