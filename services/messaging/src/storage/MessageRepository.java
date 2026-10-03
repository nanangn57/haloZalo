package storage;

import message.Message;

import java.util.List;

public interface MessageRepository {
    /**
     * Returns false when the same sender already stored the same clientMsgId.
     */
    boolean insert(Message message);

    void update(Message message);

    Message findById(String messageId);

    Message findBySenderAndClientMsgId(String senderId, String clientMsgId);

    /**
     * Messages of one conversation with seq greater than afterSeq, lowest seq first, at most limit of them.
     */
    List<Message> findAfterSeq(String conversationId, long afterSeq, int limit);
}
