package identity.group;

import identity.storage.MembershipRepository;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public final class RepositoryMembershipLookup implements MembershipLookup {
    private final MembershipRepository membershipRepository;

    public RepositoryMembershipLookup(MembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Override
    public boolean isMember(String groupId, String userId) {
        return membershipRepository.find(groupId, userId) != null;
    }

    @Override
    public Set<String> memberIds(String groupId) {
        return membershipRepository.listByGroup(groupId).stream()
            .map(Membership::getUserId)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<String> groupIdsOf(String userId) {
        return membershipRepository.listByUser(userId).stream()
            .map(Membership::getGroupId)
            .collect(Collectors.toUnmodifiableSet());
    }
}
