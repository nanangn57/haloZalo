package group;

import java.util.List;

public final class GroupListResponse {
    private final List<GroupResponse> groups;

    public GroupListResponse(List<GroupResponse> groups) {
        this.groups = groups;
    }

    public List<GroupResponse> getGroups() {
        return groups;
    }
}
