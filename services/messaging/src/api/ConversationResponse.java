package api;

import conversation.Conversation;

import java.util.List;

public final class ConversationResponse {
    private final String conversationId;
    private final String type;
    private final List<String> memberIds;

    public ConversationResponse(Conversation conversation) {
        this.conversationId = conversation.getConversationId();
        this.type = conversation.getType().name();
        this.memberIds = conversation.getMemberIds().stream().sorted().toList();
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
}
