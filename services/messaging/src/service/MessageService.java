package service;

import conversation.Conversation;
import conversation.ConversationType;
import event.EventPublisher;
import message.ContentValidator;
import message.Message;
import message.MessageContent;
import message.MessageMetadata;
import message.MessageStatus;
import reaction.MessageReaction;
import storage.ConversationRepository;
import storage.MessageRepository;
import storage.ReactionRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public final class MessageService {
    private static final String NOT_MEMBER = "Not a member of this conversation";
    public static final int CATCH_UP_DEFAULT_LIMIT = 100;
    public static final int CATCH_UP_MAX_LIMIT = 200;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ReactionRepository reactions;
    private final EventPublisher events;

    public MessageService(
        ConversationRepository conversations,
        MessageRepository messages,
        ReactionRepository reactions,
        EventPublisher events) {
        this.conversations = conversations;
        this.messages = messages;
        this.reactions = reactions;
        this.events = events;
    }

    public Conversation openConversation(String conversationId, ConversationType type, List<String> memberIds) {
        String id = conversationId == null ? UUID.randomUUID().toString() : uuid(conversationId, "conversationId");
        if (type == null || memberIds == null) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Conversation type and members are required");
        }
        Set<String> members = new HashSet<>();
        for (String member : memberIds) {
            members.add(uuid(member, "memberId"));
        }
        if (members.size() != memberIds.size()) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Members must be distinct");
        }
        if (type == ConversationType.DIRECT && members.size() != 2) {
            throw new Rejected(Rejected.Reason.VALIDATION, "A direct conversation has exactly 2 members");
        }
        if (type == ConversationType.GROUP && members.size() < 3) {
            throw new Rejected(Rejected.Reason.VALIDATION, "A group conversation has at least 3 members");
        }
        Conversation conversation = new Conversation(id, type, members, 0, now());
        if (!conversations.insert(conversation)) {
            throw new Rejected(Rejected.Reason.CONFLICT, "Conversation already exists");
        }
        return conversation;
    }

    /**
     * Stores a new message. The same sender with the same clientMsgId gets the stored message back, with no new seq.
     */
    public Sent send(
        String conversationId,
        String senderId,
        String clientMsgId,
        MessageContent content,
        MessageMetadata metadata,
        String replyTo) {
        String clientId = uuid(clientMsgId, "clientMsgId");
        String problem = ContentValidator.problem(content);
        if (problem != null) {
            throw new Rejected(Rejected.Reason.VALIDATION, problem);
        }
        String sender = user(senderId);
        Conversation conversation = memberConversation(conversationId, sender);
        Message existing = messages.findBySenderAndClientMsgId(sender, clientId);
        if (existing != null) {
            return new Sent(existing, false);
        }
        String replyId = null;
        if (replyTo != null) {
            replyId = Ids.canonical(replyTo);
            Message original = replyId == null ? null : messages.findById(replyId);
            if (original == null || !original.getConversationId().equals(conversation.getConversationId())) {
                throw new Rejected(Rejected.Reason.VALIDATION, "Reply target is not in this conversation");
            }
        }
        return store(conversation, sender, clientId, content, metadata, replyId, null);
    }

    /**
     * Forwards a message the sender can see into another conversation. forwardedFrom always points at the first original.
     */
    public Sent forward(String conversationId, String senderId, String clientMsgId, String sourceMessageId) {
        String clientId = uuid(clientMsgId, "clientMsgId");
        String sender = user(senderId);
        Conversation target = memberConversation(conversationId, sender);
        Message existing = messages.findBySenderAndClientMsgId(sender, clientId);
        if (existing != null) {
            return new Sent(existing, false);
        }
        Message source = visibleMessage(sourceMessageId, sender);
        if (source.isDeleted()) {
            throw new Rejected(Rejected.Reason.VALIDATION, "A deleted message cannot be forwarded");
        }
        String original = source.getForwardedFrom() == null ? source.getMessageId() : source.getForwardedFrom();
        return store(target, sender, clientId, source.getContent(), null, null, original);
    }

    /**
     * Messages after the client's last seen seq, deleted ones included so the client can fill every slot.
     * hasMore is false once nothing newer exists.
     */
    public CatchUp catchUp(String conversationId, String userId, Long afterSeq, Integer limit) {
        long after = afterSeq == null ? 0 : afterSeq;
        int size = limit == null ? CATCH_UP_DEFAULT_LIMIT : limit;
        if (after < 0) {
            throw new Rejected(Rejected.Reason.VALIDATION, "afterSeq must be 0 or more");
        }
        if (size < 1 || size > CATCH_UP_MAX_LIMIT) {
            throw new Rejected(Rejected.Reason.VALIDATION, "limit must be between 1 and " + CATCH_UP_MAX_LIMIT);
        }
        Conversation conversation = memberConversation(conversationId, user(userId));
        // One extra row tells whether another page exists without a count query.
        List<Message> rows = messages.findAfterSeq(conversation.getConversationId(), after, size + 1);
        boolean hasMore = rows.size() > size;
        return new CatchUp(hasMore ? rows.subList(0, size) : rows, hasMore);
    }

    /**
     * Soft delete: the row stays because replies and forwards may point at it. Only the sender can delete.
     */
    public Message delete(String conversationId, String messageId, String userId) {
        String user = user(userId);
        Message message = messageIn(conversationId, messageId, user);
        if (!message.getSenderId().equals(user)) {
            throw new Rejected(Rejected.Reason.FORBIDDEN, "Only the sender can delete a message");
        }
        if (message.isDeleted()) {
            return message;
        }
        Message deleted = message.softDeleted(now());
        messages.markDeleted(deleted);
        events.publishMessageDeleted(deleted);
        return deleted;
    }

    /**
     * Adding the same reaction twice is a no-op and publishes nothing.
     */
    public void react(String conversationId, String messageId, String userId, String code) {
        if (!MessageContent.EMOTION_CODES.contains(code)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Unknown reaction code");
        }
        String user = user(userId);
        Message message = messageIn(conversationId, messageId, user);
        if (message.isDeleted()) {
            throw new Rejected(Rejected.Reason.VALIDATION, "A deleted message cannot get reactions");
        }
        MessageReaction reaction = new MessageReaction(
            UUID.randomUUID().toString(), message.getMessageId(), user, code, now());
        if (reactions.insert(reaction)) {
            events.publishReactionAdded(message.getConversationId(), reaction);
        }
    }

    /**
     * Removing a reaction that is not there is a no-op and publishes nothing.
     */
    public void unreact(String conversationId, String messageId, String userId, String code) {
        String user = user(userId);
        Message message = messageIn(conversationId, messageId, user);
        if (reactions.delete(message.getMessageId(), user, code)) {
            events.publishReactionRemoved(message.getConversationId(), message.getMessageId(), user, code);
        }
    }

    public List<MessageReaction> reactions(String conversationId, String messageId, String userId) {
        Message message = messageIn(conversationId, messageId, user(userId));
        return reactions.findByMessageId(message.getMessageId());
    }

    private Sent store(
        Conversation conversation,
        String senderId,
        String clientMsgId,
        MessageContent content,
        MessageMetadata metadata,
        String replyTo,
        String forwardedFrom) {
        Instant now = now();
        Message draft = new Message(
            UUID.randomUUID().toString(),
            conversation.getConversationId(),
            0,
            senderId,
            clientMsgId,
            content,
            metadata,
            replyTo,
            forwardedFrom,
            MessageStatus.SENT,
            now,
            now,
            null
        );
        Message message = messages.append(draft);
        if (message == null) {
            // A concurrent retry with the same clientMsgId won. No seq was used for this attempt.
            return new Sent(messages.findBySenderAndClientMsgId(senderId, clientMsgId), false);
        }
        events.publishMessageCreated(message);
        return new Sent(message, true);
    }

    /**
     * userId must already be canonical, see {@link #user(String)}.
     */
    private Conversation memberConversation(String conversationId, String userId) {
        Conversation conversation = conversations.findById(uuid(conversationId, "conversationId"));
        // Unknown and foreign conversations look the same, so ids cannot be probed.
        if (conversation == null || !conversation.hasMember(userId)) {
            throw new Rejected(Rejected.Reason.FORBIDDEN, NOT_MEMBER);
        }
        return conversation;
    }

    private Message visibleMessage(String messageId, String userId) {
        String id = Ids.canonical(messageId);
        Message message = id == null ? null : messages.findById(id);
        if (message == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "Message not found");
        }
        memberConversation(message.getConversationId(), userId);
        return message;
    }

    /**
     * A message addressed through a conversation path must belong to that conversation.
     */
    private Message messageIn(String conversationId, String messageId, String userId) {
        Message message = visibleMessage(messageId, userId);
        if (!message.getConversationId().equals(Ids.canonical(conversationId))) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "Message not found");
        }
        return message;
    }

    private static String uuid(String value, String field) {
        String id = Ids.canonical(value);
        if (id == null) {
            throw new Rejected(Rejected.Reason.VALIDATION, field + " must be a UUID");
        }
        return id;
    }

    /**
     * The user comes from the gateway. Anything that is not a UUID did not come from Identity.
     */
    private static String user(String userId) {
        String id = Ids.canonical(userId);
        if (id == null) {
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Missing user");
        }
        return id;
    }

    private static Instant now() {
        // Kept to milliseconds so the time returned now equals the time read back later.
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public record CatchUp(List<Message> messages, boolean hasMore) {
    }

    public static final class Sent {
        private final Message message;
        private final boolean created;

        public Sent(Message message, boolean created) {
            this.message = message;
            this.created = created;
        }

        public Message getMessage() {
            return message;
        }

        public boolean isCreated() {
            return created;
        }
    }

    public static final class Rejected extends RuntimeException {
        public enum Reason {
            VALIDATION,
            UNAUTHENTICATED,
            FORBIDDEN,
            NOT_FOUND,
            CONFLICT
        }

        private final Reason reason;

        public Rejected(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }
}
