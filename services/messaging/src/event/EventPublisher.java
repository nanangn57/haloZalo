package event;

import message.Message;

public interface EventPublisher {
    void publishMessageCreated(Message message);
}
