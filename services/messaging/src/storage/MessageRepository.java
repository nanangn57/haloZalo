package storage;

import message.Message;

import java.util.List;

public interface MessageRepository {
    /**
     * Gives the message the conversation's next seq and stores it, as one atomic step. Returns the stored
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
}
