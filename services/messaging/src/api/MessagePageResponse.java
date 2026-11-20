package api;

import media.MediaLinks;
import service.MessageService;

import java.util.List;

/**
 * Body of GET /conversations/{conversationId}/messages. Messages are in seq order.
 */
public final class MessagePageResponse {
    private final List<MessageResponse> messages;
    private final boolean hasMore;

    public MessagePageResponse(MessageService.MessagePage page, MediaLinks links) {
        this.messages = page.messages().stream().map(message -> new MessageResponse(message, links)).toList();
        this.hasMore = page.hasMore();
    }

    public List<MessageResponse> getMessages() {
        return messages;
    }

    public boolean isHasMore() {
        return hasMore;
    }
}
