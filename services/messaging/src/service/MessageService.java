package service;

import conversation.Conversation;
import conversation.ConversationCursor;
import conversation.ConversationSummary;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public final class MessageService {
    private static final String NOT_MEMBER = "Not a member of this conversation";
    public static final int MESSAGES_DEFAULT_LIMIT = 100;
    public static final int MESSAGES_MAX_LIMIT = 200;
    public static final int CONVERSATIONS_DEFAULT_LIMIT = 50;
    public static final int CONVERSATIONS_MAX_LIMIT = 100;

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
     * The one DIRECT conversation between the caller and another user, created on first use. Two users opening
     * it at the same moment get the same conversation.
     */
    public Opened openDirect(String userId, String otherUserId) {
        String user = user(userId);
        String other = uuid(otherUserId, "userId");
        if (user.equals(other)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "A direct conversation needs another user");
        }
        String key = Conversation.directKey(Set.of(user, other));
        Conversation existing = conversations.findDirect(key);
        if (existing != null) {
            return new Opened(summary(existing), false);
        }
        Conversation created = new Conversation(UUID.randomUUID().toString(), ConversationType.DIRECT, Set.of(user, other), 0, now());
        if (conversations.insert(created)) {
            return new Opened(new ConversationSummary(created, null), true);
        }
        // The other user opened it between our lookup and our insert.
        Conversation winner = conversations.findDirect(key);
        if (winner == null) {
            throw new IllegalStateException("Direct conversation was neither inserted nor found: " + key);
        }
        return new Opened(summary(winner), false);
    }

    /**
     * The caller's conversations, most recent activity first, each with its newest message.
     */
    public ConversationPage conversations(String userId, String cursor, Integer limit) {
        String user = user(userId);
        int size = limit(limit, CONVERSATIONS_DEFAULT_LIMIT, CONVERSATIONS_MAX_LIMIT);
        List<Conversation> rows = conversations.findForMember(user, cursor(cursor), size + 1);
        boolean hasMore = rows.size() > size;
        List<Conversation> page = hasMore ? rows.subList(0, size) : rows;
        Map<String, Message> latest = messages.findLatest(page.stream().map(Conversation::getConversationId).toList());
        List<ConversationSummary> summaries = page.stream()
            .map(conversation -> new ConversationSummary(conversation, latest.get(conversation.getConversationId())))
            .toList();
        String next = hasMore ? ConversationCursor.after(page.get(page.size() - 1)).encode() : null;
        return new ConversationPage(summaries, next);
    }

    public ConversationSummary conversation(String conversationId, String userId) {
        return summary(memberConversation(conversationId, user(userId)));
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
     * A page of messages in seq order, deleted ones included so the client can fill every slot.
     * With afterSeq it is catch-up: the oldest messages after it, and hasMore means newer ones exist.
     * Otherwise it is history: the newest messages before beforeSeq (or the newest of all), and hasMore means
     * older ones exist.
     */
    public MessagePage messages(String conversationId, String userId, Long afterSeq, Long beforeSeq, Integer limit) {
        if (afterSeq != null && beforeSeq != null) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Use afterSeq or beforeSeq, not both");
        }
        if (afterSeq != null && afterSeq < 0) {
            throw new Rejected(Rejected.Reason.VALIDATION, "afterSeq must be 0 or more");
        }
        if (beforeSeq != null && beforeSeq < 1) {
            throw new Rejected(Rejected.Reason.VALIDATION, "beforeSeq must be 1 or more");
        }
        int size = limit(limit, MESSAGES_DEFAULT_LIMIT, MESSAGES_MAX_LIMIT);
        String id = memberConversation(conversationId, user(userId)).getConversationId();
        // One extra row tells whether another page exists without a count query.
        if (afterSeq != null) {
            List<Message> rows = messages.findAfterSeq(id, afterSeq, size + 1);
            boolean hasMore = rows.size() > size;
            return new MessagePage(hasMore ? rows.subList(0, size) : rows, hasMore);
        }
        List<Message> rows = messages.findBeforeSeq(id, beforeSeq == null ? Long.MAX_VALUE : beforeSeq, size + 1);
        boolean hasMore = rows.size() > size;
        // Rows are in seq order, so the extra one is the oldest.
        return new MessagePage(hasMore ? rows.subList(1, rows.size()) : rows, hasMore);
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

    private ConversationSummary summary(Conversation conversation) {
        Map<String, Message> latest = messages.findLatest(List.of(conversation.getConversationId()));
        return new ConversationSummary(conversation, latest.get(conversation.getConversationId()));
    }

    private static int limit(Integer limit, int fallback, int max) {
        int size = limit == null ? fallback : limit;
        if (size < 1 || size > max) {
            throw new Rejected(Rejected.Reason.VALIDATION, "limit must be between 1 and " + max);
        }
        return size;
    }

    private static ConversationCursor cursor(String value) {
        if (value == null) {
            return null;
        }
        int split = value.indexOf('_');
        String id = split < 0 ? null : Ids.canonical(value.substring(split + 1));
        try {
            if (id != null) {
                return new ConversationCursor(Instant.ofEpochMilli(Long.parseLong(value.substring(0, split))), id);
            }
        } catch (NumberFormatException ex) {
            // Falls through to the rejection below.
        }
        throw new Rejected(Rejected.Reason.VALIDATION, "cursor is not valid");
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

    public record MessagePage(List<Message> messages, boolean hasMore) {
    }

    /**
     * nextCursor is null on the last page.
     */
    public record ConversationPage(List<ConversationSummary> conversations, String nextCursor) {
    }

    public record Opened(ConversationSummary conversation, boolean created) {
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
