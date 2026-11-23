package identity.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class RegisterRequest {
    private final String username;
    private final String email;
    private final String password;
    private final String displayName;

    @JsonCreator
    public RegisterRequest(
        @JsonProperty("username") String username,
        @JsonProperty("email") String email,
        @JsonProperty("password") String password,
        @JsonProperty("displayName") String displayName) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    /** Test helper: displayName mặc định = username (hoặc local-part email). */
    public RegisterRequest(String username, String email, String password) {
        this(
            username,
            email,
            password,
            username != null && !username.isBlank()
                ? username
                : (email != null && email.contains("@") ? email.substring(0, email.indexOf('@')) : "User")
        );
    }

    /** Optional. When missing, the service derives it from the email local part. */
    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getDisplayName() {
        return displayName;
    }
}
