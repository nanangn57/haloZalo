package identity.access;

import identity.group.MembershipLookup;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public final class MembershipRequestAuthorizer implements RequestAuthorizer {
    private final MembershipLookup memberships;
    private final ConversationAccess conversations;

    public MembershipRequestAuthorizer(MembershipLookup memberships, ConversationAccess conversations) {
        this.memberships = memberships;
        this.conversations = conversations;
    }

    @Override
    public void authorize(String userId, String path) {
        String groupId = groupId(path);
        if (groupId != null && !memberships.isMember(canonical(groupId), userId)) {
            throw new Forbidden("Not allowed to change this group");
        }
        String conversationId = conversationId(path);
        if (conversationId != null && !conversations.canEnter(userId, canonical(conversationId))) {
            throw new Forbidden("Not a member of this conversation");
        }
    }

    static String groupId(String path) {
        return firstSegment(path, "/groups/");
    }

    static String conversationId(String path) {
        String id = firstSegment(path, "/conversations/");
        if (id == null || "direct".equals(id)) {
            return null;
        }
        return id;
    }

    private static String firstSegment(String path, String prefix) {
        if (path == null || !path.startsWith(prefix)) {
            return null;
        }
        String rest = path.substring(prefix.length());
        if (rest.isEmpty()) {
            return null;
        }
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    private static String canonical(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }
}
