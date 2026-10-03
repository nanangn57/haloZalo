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
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public final class MessageService {
    private static final Pattern UUID_PATTERN =
        Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final String NOT_MEMBER = "Not a member of this conversation";

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
        String id = conversationId == null ? UUID.randomUUID().toString() : conversationId;
        requireUuid(id, "conversationId");
        if (type == null || memberIds == null) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Conversation type and members are required");
        }
        Set<String> members = new HashSet<>(memberIds);
        if (members.size() != memberIds.size()) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Members must be distinct");
        }
        members.forEach(member -> requireUuid(member, "memberId"));
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
        requireUuid(clientMsgId, "clientMsgId");
        String problem = ContentValidator.problem(content);
        if (problem != null) {
            throw new Rejected(Rejected.Reason.VALIDATION, problem);
        }
        Conversation conversation = memberConversation(conversationId, senderId);
        Message existing = messages.findBySenderAndClientMsgId(senderId, clientMsgId);
        if (existing != null) {
            return new Sent(existing, false);
        }
        if (replyTo != null) {
            Message original = messages.findById(replyTo);
            if (original == null || !original.getConversationId().equals(conversation.getConversationId())) {
                throw new Rejected(Rejected.Reason.VALIDATION, "Reply target is not in this conversation");
            }
        }
        return store(conversation, senderId, clientMsgId, content, metadata, replyTo, null);
    }

    /**
     * Forwards a message the sender can see into another conversation. forwardedFrom always points at the first original.
     */
    public Sent forward(String conversationId, String senderId, String clientMsgId, String sourceMessageId) {
        requireUuid(clientMsgId, "clientMsgId");
        Conversation target = memberConversation(conversationId, senderId);
        Message existing = messages.findBySenderAndClientMsgId(senderId, clientMsgId);
        if (existing != null) {
            return new Sent(existing, false);
        }
        Message source = visibleMessage(sourceMessageId, senderId);
        if (source.isDeleted()) {
            throw new Rejected(Rejected.Reason.VALIDATION, "A deleted message cannot be forwarded");
        }
        String original = source.getForwardedFrom() == null ? source.getMessageId() : source.getForwardedFrom();
        return store(target, senderId, clientMsgId, source.getContent(), null, null, original);
    }

    /**
     * Soft delete: the row stays because replies and forwards may point at it. Only the sender can delete.
     */
    public Message delete(String conversationId, String messageId, String userId) {
        Message message = messageIn(conversationId, messageId, userId);
        if (!message.getSenderId().equals(userId)) {
            throw new Rejected(Rejected.Reason.FORBIDDEN, "Only the sender can delete a message");
        }
        if (message.isDeleted()) {
            return message;
        }
        Message deleted = message.softDeleted(now());
        messages.update(deleted);
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
        Message message = messageIn(conversationId, messageId, userId);
        if (message.isDeleted()) {
            throw new Rejected(Rejected.Reason.VALIDATION, "A deleted message cannot get reactions");
        }
        MessageReaction reaction = new MessageReaction(UUID.randomUUID().toString(), messageId, userId, code, now());
        if (reactions.insert(reaction)) {
            events.publishReactionAdded(conversationId, reaction);
        }
    }

    /**
     * Removing a reaction that is not there is a no-op and publishes nothing.
     */
    public void unreact(String conversationId, String messageId, String userId, String code) {
        messageIn(conversationId, messageId, userId);
        if (reactions.delete(messageId, userId, code)) {
            events.publishReactionRemoved(conversationId, messageId, userId, code);
        }
    }

    public List<MessageReaction> reactions(String conversationId, String messageId, String userId) {
        messageIn(conversationId, messageId, userId);
        return reactions.findByMessageId(messageId);
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
        Message message = new Message(
            UUID.randomUUID().toString(),
            conversation.getConversationId(),
            conversations.nextSeq(conversation.getConversationId()),
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
        if (!messages.insert(message)) {
            // A concurrent retry with the same clientMsgId won. Its seq is the real one; ours is left as a gap.
            return new Sent(messages.findBySenderAndClientMsgId(senderId, clientMsgId), false);
        }
        events.publishMessageCreated(message);
        return new Sent(message, true);
    }

    private Conversation memberConversation(String conversationId, String userId) {
        requireUuid(conversationId, "conversationId");
        if (userId == null || userId.isBlank()) {
            throw new Rejected(Rejected.Reason.UNAUTHENTICATED, "Missing user");
        }
        Conversation conversation = conversations.findById(conversationId);
        // Unknown and foreign conversations look the same, so ids cannot be probed.
        if (conversation == null || !conversation.hasMember(userId)) {
            throw new Rejected(Rejected.Reason.FORBIDDEN, NOT_MEMBER);
        }
        return conversation;
    }

    private Message visibleMessage(String messageId, String userId) {
        Message message = messageId == null ? null : messages.findById(messageId);
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
        if (!message.getConversationId().equals(conversationId)) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "Message not found");
        }
        return message;
    }

    private static void requireUuid(String value, String field) {
        if (value == null || !UUID_PATTERN.matcher(value).matches()) {
            throw new Rejected(Rejected.Reason.VALIDATION, field + " must be a UUID");
        }
    }

    private static Instant now() {
        // Mongo keeps milliseconds, so the stored and the returned time must match.
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
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
