package storage;

import message.Message;

public interface MessageRepository {
    /**
     * Returns false when the same sender already stored the same clientMsgId.
     */
    boolean insert(Message message);

    void update(Message message);

    Message findById(String messageId);

    Message findBySenderAndClientMsgId(String senderId, String clientMsgId);
}
