package identity.group;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;

public class AddMemberRequest {
    private final String userId;

    @JsonCreator
    public AddMemberRequest(
        @JsonProperty("userId") String userId
    ) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}
