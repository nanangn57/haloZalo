package conversation;

import message.Message;

/**
 * A conversation with its newest message, as the conversation list shows it. lastMessage is null before the first one.
 */
public record ConversationSummary(Conversation conversation, Message lastMessage) {
}
