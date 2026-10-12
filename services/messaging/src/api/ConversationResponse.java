package api;

import conversation.Conversation;
import conversation.ConversationSummary;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One conversation. lastMessage and lastMessageAt are left out before the first message.
 * lastReadSeq and unreadCount are the caller's, and left out on internal calls that have no caller.
 */
public final class ConversationResponse {
    private final String conversationId;
    private final String type;
    private final List<String> memberIds;
    private final long lastSeq;
    private final String createdAt;
    private final String lastMessageAt;
    private final MessageResponse lastMessage;
    private final Long lastReadSeq;
    private final Long unreadCount;

    public ConversationResponse(ConversationSummary summary) {
        Conversation conversation = summary.conversation();
        this.conversationId = conversation.getConversationId();
        this.type = conversation.getType().name();
        this.memberIds = summary.memberIds().stream().sorted().toList();
        this.lastSeq = conversation.getLastSeq();
        this.createdAt = conversation.getCreatedAt().toString();
        this.lastMessageAt = conversation.getLastMessageAt() == null ? null : conversation.getLastMessageAt().toString();
        this.lastMessage = summary.lastMessage() == null ? null : new MessageResponse(summary.lastMessage());
        this.lastReadSeq = summary.read() == null ? null : summary.read().lastReadSeq();
        this.unreadCount = summary.read() == null ? null : summary.read().unreadCount();
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getType() {
        return type;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public long getLastSeq() {
        return lastSeq;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getLastMessageAt() {
        return lastMessageAt;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public MessageResponse getLastMessage() {
        return lastMessage;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long getLastReadSeq() {
        return lastReadSeq;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long getUnreadCount() {
        return unreadCount;
    }
}
