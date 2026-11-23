package service;

import message.Message;

/**
 * What other modules may ask of messaging when something happens that members should see in the chat.
 */
public interface SystemMessagePoster {
    /**
     * Stores a SYSTEM message with the given text in the conversation and returns it.
     */
    Message postSystem(String conversationId, String text);
}
