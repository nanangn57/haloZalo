package identity.group;

import java.util.List;

public final class MemberListResponse {
    private final List<GroupMemberResponse> members;

    public MemberListResponse(List<GroupMemberResponse> members) {
        this.members = members;
    }

    public List<GroupMemberResponse> getMembers() {
        return members;
    }
}
