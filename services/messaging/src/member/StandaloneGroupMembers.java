package member;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory membership for tests / no-DB profiles. Not used in profile {@code app}.
 */
@Component
@Profile("!app")
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
