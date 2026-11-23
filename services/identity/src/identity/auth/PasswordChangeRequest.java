package identity.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class PasswordChangeRequest {
    private final String currentPassword;
    private final String newPassword;
    private final Boolean revokeOtherSessions;

    @JsonCreator
    public PasswordChangeRequest(
        @JsonProperty("currentPassword") String currentPassword,
        @JsonProperty("newPassword") String newPassword,
        @JsonProperty("revokeOtherSessions") Boolean revokeOtherSessions) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.revokeOtherSessions = revokeOtherSessions;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public Boolean getRevokeOtherSessions() {
        return revokeOtherSessions;
    }
}
