package event;

import message.Message;
import reaction.MessageReaction;

import java.util.ArrayList;
import java.util.List;

public final class RecordingEvents implements EventPublisher {
    public final List<Message> created = new ArrayList<>();
    public final List<Message> deleted = new ArrayList<>();
    public final List<String> reactions = new ArrayList<>();

    @Override
    public void publishMessageCreated(Message message) {
        created.add(message);
    }

    @Override
    public void publishMessageDeleted(Message message) {
        deleted.add(message);
    }

    @Override
    public void publishReactionAdded(String conversationId, MessageReaction reaction) {
        reactions.add("added " + conversationId + " " + reaction.getMessageId() + " " + reaction.getUserId() + " " + reaction.getCode());
    }

    @Override
    public void publishReactionRemoved(String conversationId, String messageId, String userId, String code) {
        reactions.add("removed " + conversationId + " " + messageId + " " + userId + " " + code);
    }
}
