package storage;

import message.Message;

import java.util.List;

public interface MessageSearchRepository {
    /**
     * Not-deleted TEXT messages of one conversation with seq greater than afterSeq whose text contains the given
     * text, ignoring case. Lowest seq first, at most limit of them.
     */
    List<Message> searchAfterSeq(String conversationId, long afterSeq, String text, int limit);

    /**
     * The newest limit matching messages with seq less than beforeSeq, returned lowest seq first.
     */
    List<Message> searchBeforeSeq(String conversationId, long beforeSeq, String text, int limit);
}
