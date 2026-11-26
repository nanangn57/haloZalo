package identity.group;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.List;

public class CreateGroupRequest {
    private final String name;
    private final List<String> memberIds;

    public CreateGroupRequest(String name) {
        this(name, null);
    }

    @JsonCreator
    public CreateGroupRequest(
        @JsonProperty("name") String name,
        @JsonProperty("memberIds") List<String> memberIds
    ) {
        this.name = name;
        this.memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
    }

    public String getName() {
        return name;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }
}
