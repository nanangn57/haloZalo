package auth;

public final class RegisterAccepted {
    private final String email;

    public RegisterAccepted(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
