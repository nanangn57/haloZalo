package identity.access;

import identity.group.MembershipLookup;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class MembershipConversationAccess implements ConversationAccess {
    private final MembershipLookup memberships;
    private final DirectConversationAccess direct;

    @Autowired
    public MembershipConversationAccess(MembershipLookup memberships, ObjectProvider<DirectConversationAccess> direct) {
        this(memberships, direct.getIfAvailable());
    }

    MembershipConversationAccess(MembershipLookup memberships, DirectConversationAccess direct) {
        this.memberships = memberships;
        this.direct = direct;
    }

    @Override
    public boolean canEnter(String userId, String conversationId) {
        if (memberships.isMember(conversationId, userId)) {
            return true;
        }
        return direct != null && direct.isMember(conversationId, userId);
    }
}
