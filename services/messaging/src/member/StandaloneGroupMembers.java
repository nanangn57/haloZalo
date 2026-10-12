package member;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * TEMPORARY: lets messaging run on its own until the app wires identity's MembershipLookup into
 * {@link GroupMembers}. Held in memory only and filled through StandaloneGroupController, so nothing is copied into
 * the messaging schema. Delete this class and StandaloneGroupController in the same change that adds the real
 * adapter.
 */
@Component
public final class StandaloneGroupMembers implements GroupMembers {
    private final Map<String, Set<String>> membersByGroup = new ConcurrentHashMap<>();

    public void replace(String groupId, Set<String> memberIds) {
        membersByGroup.put(groupId, Set.copyOf(memberIds));
    }

    @Override
    public boolean isMember(String groupId, String userId) {
        return memberIds(groupId).contains(userId);
    }

    @Override
    public Set<String> memberIds(String groupId) {
        return membersByGroup.getOrDefault(groupId, Set.of());
    }

    @Override
    public Set<String> groupIdsOf(String userId) {
        return membersByGroup.entrySet().stream()
            .filter(entry -> entry.getValue().contains(userId))
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
    }
}
