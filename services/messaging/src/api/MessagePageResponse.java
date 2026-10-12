package api;

import service.MessageService;

import java.util.List;

/**
 * Body of GET /conversations/{conversationId}/messages. Messages are in seq order.
 */
public final class MessagePageResponse {
    private final List<MessageResponse> messages;
    private final boolean hasMore;

    public MessagePageResponse(MessageService.MessagePage page) {
        this.messages = page.messages().stream().map(MessageResponse::new).toList();
        this.hasMore = page.hasMore();
    }

    public List<MessageResponse> getMessages() {
        return messages;
    }

    public boolean isHasMore() {
        return hasMore;
    }
}
