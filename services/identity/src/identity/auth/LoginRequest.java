package identity.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class LoginRequest {
    private final String login;
    private final String password;
    private final String deviceId;
    private final String deviceLabel;
    private final String userAgent;

    @JsonCreator
    public LoginRequest(
        @JsonProperty("login") String login,
        @JsonProperty("password") String password,
        @JsonProperty("deviceId") String deviceId,
        @JsonProperty("deviceLabel") String deviceLabel,
        @JsonProperty("userAgent") String userAgent
    ) {
        this.login = login;
        this.password = password;
        this.deviceId = deviceId;
        this.deviceLabel = deviceLabel;
        this.userAgent = userAgent;
    }

    /** Helper test / call site ngắn. */
    public LoginRequest(String login, String password, String deviceId) {
        this(login, password, deviceId, null, null);
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
