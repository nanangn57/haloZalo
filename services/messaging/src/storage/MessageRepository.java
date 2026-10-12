package storage;

import message.Message;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MessageRepository {
    /**
     * Gives the message the conversation's next seq, stores it, and marks the conversation active at the
     * message's createdAt, as one atomic step. Returns the stored
     * message, or null when the same sender already stored the same clientMsgId; then no seq is used.
     */
    Message append(Message draft);

    /**
     * Stores status, updatedAt and deletedAt of a soft-deleted message. Nothing else of a message changes.
     */
    void markDeleted(Message deleted);

    Message findById(String messageId);

    Message findBySenderAndClientMsgId(String senderId, String clientMsgId);

    /**
     * Messages of one conversation with seq greater than afterSeq, lowest seq first, at most limit of them.
     */
    List<Message> findAfterSeq(String conversationId, long afterSeq, int limit);

    /**
     * The newest limit messages of one conversation with seq less than beforeSeq, returned lowest seq first.
     */
    List<Message> findBeforeSeq(String conversationId, long beforeSeq, int limit);

    /**
     * The newest message of each given conversation, by conversation id. Conversations without messages are absent.
     */
    Map<String, Message> findLatest(Collection<String> conversationIds);
}
