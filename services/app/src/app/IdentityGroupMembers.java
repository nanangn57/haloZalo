package app;

import identity.group.MembershipLookup;
import member.GroupMembers;

import java.util.Set;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * In-process adapter: messaging GroupMembers → identity MembershipLookup. No cross-schema JDBC.
 */
@Component
@Primary
@Profile("app")
public final class IdentityGroupMembers implements GroupMembers {
    private final MembershipLookup memberships;

    public IdentityGroupMembers(MembershipLookup memberships) {
        this.memberships = memberships;
    }

    @Override
    public boolean isMember(String groupId, String userId) {
        return memberships.isMember(groupId, userId);
    }

    @Override
    public Set<String> memberIds(String groupId) {
        return memberships.memberIds(groupId);
    }

    @Override
    public Set<String> groupIdsOf(String userId) {
        return memberships.groupIdsOf(userId);
    }
}
