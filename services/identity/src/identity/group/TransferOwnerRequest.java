package identity.group;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class TransferOwnerRequest {
    private final String userId;

    @JsonCreator
    public TransferOwnerRequest(@JsonProperty("userId") String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}
