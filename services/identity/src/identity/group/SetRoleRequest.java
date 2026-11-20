package identity.group;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class SetRoleRequest {
    private final String role;

    @JsonCreator
    public SetRoleRequest(@JsonProperty("role") String role) {
        this.role = role;
    }

    public String getRole() {
        return role;
    }
}
