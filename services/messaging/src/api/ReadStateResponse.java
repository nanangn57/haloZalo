package api;

import conversation.ReadState;
import service.Ids;

/**
 * Body returned by PUT /conversations/{conversationId}/read.
 */
public final class ReadStateResponse {
    private final String conversationId;
    private final long lastReadSeq;
    private final long unreadCount;

    public ReadStateResponse(String conversationId, ReadState state) {
        this.conversationId = Ids.canonical(conversationId);
        this.lastReadSeq = state.lastReadSeq();
        this.unreadCount = state.unreadCount();
    }

    public String getConversationId() {
        return conversationId;
    }

    public long getLastReadSeq() {
        return lastReadSeq;
    }

    public long getUnreadCount() {
        return unreadCount;
    }
}
