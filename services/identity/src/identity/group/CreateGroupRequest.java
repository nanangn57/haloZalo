package identity.group;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;

public class CreateGroupRequest {
    private final String name;

    @JsonCreator
    public CreateGroupRequest(
        @JsonProperty("name") String name
    ) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
