package api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of POST /conversations/direct: the other user of the 1-1 conversation.
 */
public final class OpenDirectRequest {
    private final String userId;

    @JsonCreator
    public OpenDirectRequest(@JsonProperty("userId") String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}
