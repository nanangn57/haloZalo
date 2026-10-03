package api;

import service.MessageService;

import java.util.List;

/**
 * Body of GET /conversations/{conversationId}/messages. Messages are in seq order.
 */
public final class CatchUpResponse {
    private final List<MessageResponse> messages;
    private final boolean hasMore;

    public CatchUpResponse(MessageService.CatchUp catchUp) {
        this.messages = catchUp.messages().stream().map(MessageResponse::new).toList();
        this.hasMore = catchUp.hasMore();
    }

    public List<MessageResponse> getMessages() {
        return messages;
    }

    public boolean isHasMore() {
        return hasMore;
    }
}
