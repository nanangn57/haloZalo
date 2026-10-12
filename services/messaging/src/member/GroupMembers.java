package member;

import java.util.Set;

/**
 * Group membership, owned by identity. A GROUP conversation has the id of its identity group.
 * In the single app this is backed by identity's MembershipLookup. Messaging never stores group members.
 */
public interface GroupMembers {
    boolean isMember(String groupId, String userId);

    /**
     * Everyone in the group, for pushing events to all of them.
     */
    Set<String> memberIds(String groupId);

    /**
     * The groups the user is in, for the conversation list.
     */
    Set<String> groupIdsOf(String userId);
}
