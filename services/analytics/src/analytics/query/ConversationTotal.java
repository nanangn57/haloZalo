package analytics.query;

public final class ConversationTotal {
    private final String conversationId;
    private final long total;

    public ConversationTotal(String conversationId, long total) {
        this.conversationId = conversationId;
        this.total = total;
    }

    public String getConversationId() {
        return conversationId;
    }

    public long getTotal() {
        return total;
    }
}
