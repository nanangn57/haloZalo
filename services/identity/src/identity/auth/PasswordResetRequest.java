package identity.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class PasswordResetRequest {
    private final String token;
    private final String password;

    @JsonCreator
    public PasswordResetRequest(
        @JsonProperty("token") String token,
        @JsonProperty("password") String password) {
        this.token = token;
        this.password = password;
    }

    public String getToken() {
        return token;
    }

    public String getPassword() {
        return password;
    }
}
