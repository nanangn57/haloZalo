package identity.group;

import java.util.Set;

public interface MembershipLookup {
    boolean isMember(String groupId, String userId);

    Set<String> memberIds(String groupId);

    Set<String> groupIdsOf(String userId);
}
