package member;

import conversation.Conversation;
import conversation.ConversationType;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Who is in a conversation. DIRECT: the two people messaging stored when it opened the conversation.
 * GROUP: whoever identity says is in the group right now, so a removed member stops getting messages at once.
 */
@Component
public final class ConversationMembers {
    private final GroupMembers groups;

    public ConversationMembers(GroupMembers groups) {
        this.groups = groups;
    }

    public boolean isMember(Conversation conversation, String userId) {
        if (conversation.getType() == ConversationType.DIRECT) {
            return conversation.hasMember(userId);
        }
        return groups.isMember(conversation.getConversationId(), userId);
    }

    public Set<String> memberIds(Conversation conversation) {
        if (conversation.getType() == ConversationType.DIRECT) {
            return conversation.getMemberIds();
        }
        return groups.memberIds(conversation.getConversationId());
    }

    public Set<String> groupIdsOf(String userId) {
        return groups.groupIdsOf(userId);
    }
}
