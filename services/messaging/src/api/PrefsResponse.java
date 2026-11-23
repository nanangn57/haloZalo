package api;

import service.MessageService;

/**
 * Body of PATCH /conversations/{conversationId}/prefs: the caller's prefs after the change.
 */
public final class PrefsResponse {
    private final String conversationId;
    private final boolean pinned;
    private final boolean muted;
    private final boolean archived;

    public PrefsResponse(MessageService.PrefsChange change) {
        this.conversationId = change.conversationId();
        this.pinned = change.prefs().pinned();
        this.muted = change.prefs().muted();
        this.archived = change.prefs().archived();
    }

    public String getConversationId() {
        return conversationId;
    }

    public boolean isPinned() {
        return pinned;
    }

    public boolean isMuted() {
        return muted;
    }

    public boolean isArchived() {
        return archived;
    }
}
