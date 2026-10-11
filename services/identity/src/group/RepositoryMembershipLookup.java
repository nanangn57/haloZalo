package group;

import storage.MembershipRepository;
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

}
