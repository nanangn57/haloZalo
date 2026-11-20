package api;

import media.MediaLinks;
import service.MessageService;

import java.util.List;

/**
 * Body of GET /conversations. Pass nextCursor back as cursor for the next page; it is null on the last page.
 */
public final class ConversationListResponse {
    private final List<ConversationResponse> conversations;
    private final String nextCursor;

    public ConversationListResponse(MessageService.ConversationPage page, MediaLinks links) {
        this.conversations = page.conversations().stream().map(summary -> new ConversationResponse(summary, links)).toList();
        this.nextCursor = page.nextCursor();
    }

    public List<ConversationResponse> getConversations() {
        return conversations;
    }

    public String getNextCursor() {
        return nextCursor;
    }
}
