package identity.friend;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class BlockBody {
    private final String userId;

    @JsonCreator
    public BlockBody(@JsonProperty("userId") String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}
