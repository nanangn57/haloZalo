package identity.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class ResendVerificationRequest {
    private final String email;

    @JsonCreator
    public ResendVerificationRequest(
        @JsonProperty("email") String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
