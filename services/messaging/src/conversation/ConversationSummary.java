package conversation;

import message.Message;

import java.util.Set;

/**
 * A conversation with its members and newest message, as clients see it. lastMessage is null before the first one.
 * memberIds is resolved for both types: the stored pair for DIRECT, identity's members for GROUP.
 */
public record ConversationSummary(Conversation conversation, Set<String> memberIds, Message lastMessage) {
}
