package conversation;

import message.Message;

import java.util.Set;

/**
 * A conversation with its members and newest message, as clients see it. lastMessage is null before the first one.
 * memberIds is resolved for both types: the stored pair for DIRECT, identity's members for GROUP.
 * read is the caller's read state, null when there is no caller (internal calls).
 * prefs is the caller's own pin / mute / archive, null when there is no caller.
 * pendingRequest is true when the caller is the recipient of a stranger's request not accepted yet.
 */
public record ConversationSummary(
    Conversation conversation,
    Set<String> memberIds,
    Message lastMessage,
    ReadState read,
    ConversationPrefs prefs,
    boolean pendingRequest) {

    public ConversationSummary(Conversation conversation, Set<String> memberIds, Message lastMessage, ReadState read) {
        this(conversation, memberIds, lastMessage, read, null, false);
    }
}
