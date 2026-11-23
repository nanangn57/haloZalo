package service;

import conversation.Conversation;
import conversation.ConversationCursor;
import conversation.ConversationFilter;
import conversation.ConversationPrefs;
import conversation.ConversationPrefsPatch;
import conversation.ConversationSummary;
import conversation.ConversationType;
import conversation.ReadState;
import event.EventPublisher;
import member.BlockChecker;
import member.ConversationMembers;
import member.FriendshipChecker;
import message.ContentValidator;
import message.Message;
import message.MessageContent;
import message.MessageMetadata;
import message.MessageStatus;
import message.MessageType;
import reaction.MessageReaction;
import storage.ConversationPrefsRepository;
import storage.ConversationRepository;
import storage.MessageSearchRepository;
import storage.MessageRepository;
import storage.ReactionRepository;
import storage.ReadMarkerRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public final class MessageService implements SystemMessagePoster {
    private static final String NOT_MEMBER = "Not a member of this conversation";
    private static final String BLOCKED = "Blocked";
    public static final int MESSAGES_DEFAULT_LIMIT = 100;
    public static final int MESSAGES_MAX_LIMIT = 200;
    public static final int SEARCH_MAX_LENGTH = 256;
    public static final int CONVERSATIONS_DEFAULT_LIMIT = 50;
    public static final int CONVERSATIONS_MAX_LIMIT = 100;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final MessageSearchRepository search;
    private final ReactionRepository reactions;
    private final ReadMarkerRepository readMarkers;
    private final ConversationPrefsRepository prefs;
    private final ConversationMembers members;
    private final FriendshipChecker friendships;
    private final BlockChecker blocks;
    private final EventPublisher events;

    public MessageService(
        ConversationRepository conversations,
        MessageRepository messages,
        MessageSearchRepository search,
        ReactionRepository reactions,
        ReadMarkerRepository readMarkers,
        ConversationPrefsRepository prefs,
        ConversationMembers members,
        FriendshipChecker friendships,
        BlockChecker blocks,
        EventPublisher events) {
        this.conversations = conversations;
        this.messages = messages;
        this.search = search;
        this.reactions = reactions;
        this.readMarkers = readMarkers;
        this.prefs = prefs;
        this.members = members;
        this.friendships = friendships;
        this.blocks = blocks;
        this.events = events;
    }

    /**
     * DIRECT: stores the two members. GROUP: the id is the identity group's id, and members are never given here
     * because identity owns them.
     */
    public ConversationSummary openConversation(String conversationId, ConversationType type, List<String> memberIds) {
        Conversation opened = type == ConversationType.GROUP
            ? openGroup(conversationId, memberIds)
            : openDirectWithMembers(conversationId, type, memberIds);
        return summary(opened, null);
    }

    private Conversation openDirectWithMembers(String conversationId, ConversationType type, List<String> memberIds) {
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
        if (members.size() != 2) {
            throw new Rejected(Rejected.Reason.VALIDATION, "A direct conversation has exactly 2 members");
        }
        Conversation conversation = new Conversation(id, type, members, 0, now());
        if (!conversations.insert(conversation)) {
            throw new Rejected(Rejected.Reason.CONFLICT, "Conversation already exists");
        }
        return conversation;
    }

    private Conversation openGroup(String groupId, List<String> memberIds) {
        if (groupId == null) {
            throw new Rejected(Rejected.Reason.VALIDATION, "A group conversation uses the group's id as conversationId");
        }
        if (memberIds != null && !memberIds.isEmpty()) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Group members come from identity, not from this request");
        }
        Conversation conversation = new Conversation(uuid(groupId, "conversationId"), ConversationType.GROUP, Set.of(), 0, now());
        if (!conversations.insert(conversation)) {
            throw new Rejected(Rejected.Reason.CONFLICT, "Conversation already exists");
        }
        return conversation;
    }

    /**
     * Opens the GROUP conversation whose id is the identity groupId. Caller must be a member.
     */
    public Opened openGroupForMember(String userId, String groupId) {
        String user = user(userId);
        String id = uuid(groupId, "groupId");
        if (!members.groupIdsOf(user).contains(id)) {
            throw new Rejected(Rejected.Reason.FORBIDDEN, "Not a member of this conversation");
        }
        Conversation existing = conversations.findById(id);
        if (existing != null) {
            return new Opened(summary(existing, user), false);
        }
        try {
            Conversation created = openGroup(id, List.of());
            return new Opened(summary(created, user), true);
        } catch (Rejected rejected) {
            if (rejected.getReason() != Rejected.Reason.CONFLICT) {
                throw rejected;
            }
            Conversation winner = conversations.findById(id);
            if (winner == null) {
                throw rejected;
            }
            return new Opened(summary(winner, user), false);
        }
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
        if (blocks.isBlocked(user, other)) {
            throw new Rejected(Rejected.Reason.FORBIDDEN, BLOCKED);
        }
        String key = Conversation.directKey(Set.of(user, other));
        Conversation existing = conversations.findDirect(key);
        if (existing != null) {
            return new Opened(summary(settled(existing), user), false);
        }
        // A stranger's first chat is a request for the other side until they accept.
        String pendingFor = friendships.areFriends(user, other) ? null : other;
        Conversation created = new Conversation(
            UUID.randomUUID().toString(), ConversationType.DIRECT, Set.of(user, other), 0, now(), null, pendingFor);
        if (conversations.insert(created)) {
            return new Opened(new ConversationSummary(created, created.getMemberIds(), null, new ReadState(0, 0),
                ConversationPrefs.DEFAULT, created.isPendingFor(user)), true);
        }
        // The other user opened it between our lookup and our insert.
        Conversation winner = conversations.findDirect(key);
        if (winner == null) {
            throw new IllegalStateException("Direct conversation was neither inserted nor found: " + key);
        }
        return new Opened(summary(settled(winner), user), false);
    }

    /**
     * The caller's conversations in the normal inbox, pinned first, then most recent activity first.
     */
    public ConversationPage conversations(String userId, String cursor, Integer limit) {
        return conversations(userId, cursor, limit, ConversationFilter.DEFAULT);
    }

    /**
     * The filter chooses archived or not, and the stranger-request inbox. A request whose two users became friends
     * since it was made is accepted here instead of listed.
     */
    public ConversationPage conversations(String userId, String cursor, Integer limit, ConversationFilter filter) {
        String user = user(userId);
        int size = limit(limit, CONVERSATIONS_DEFAULT_LIMIT, CONVERSATIONS_MAX_LIMIT);
        List<Conversation> rows = conversations.findForMember(
            user, members.groupIdsOf(user), filter, cursor(cursor), size + 1);
        boolean hasMore = rows.size() > size;
        List<Conversation> found = hasMore ? rows.subList(0, size) : rows;
        List<String> foundIds = found.stream().map(Conversation::getConversationId).toList();
        Map<String, ConversationPrefs> foundPrefs = prefs.find(user, foundIds);
        String next = hasMore
            ? ConversationCursor.after(found.get(found.size() - 1), foundPrefs
                .getOrDefault(found.get(found.size() - 1).getConversationId(), ConversationPrefs.DEFAULT).pinned()).encode()
            : null;
        List<Conversation> page = filter.requests() ? found.stream().filter(row -> stillPending(row, user)).toList() : found;
        List<String> ids = page.stream().map(Conversation::getConversationId).toList();
        Map<String, Message> latest = messages.findLatest(ids);
        Map<String, ReadState> read = readMarkers.readStates(user, ids);
        List<ConversationSummary> summaries = page.stream()
            .map(conversation -> new ConversationSummary(conversation, members.memberIds(conversation),
                latest.get(conversation.getConversationId()), read.get(conversation.getConversationId()),
                foundPrefs.getOrDefault(conversation.getConversationId(), ConversationPrefs.DEFAULT),
                conversation.isPendingFor(user)))
            .toList();
        return new ConversationPage(summaries, next);
    }

    /**
     * Pin, mute or archive for the caller only. Fields left null stay as they are.
     */
    public PrefsChange updatePrefs(String conversationId, String userId, ConversationPrefsPatch patch) {
        if (patch == null || patch.isEmpty()) {
            throw new Rejected(Rejected.Reason.VALIDATION, "At least one of pinned, muted, archived is required");
        }
        String user = user(userId);
        Conversation conversation = memberConversation(conversationId, user);
        ConversationPrefs changed = prefs.update(user, conversation.getConversationId(), patch, now());
        return new PrefsChange(conversation.getConversationId(), changed);
    }

    /**
     * The recipient of a stranger's request takes it into the normal inbox. Accepting twice is fine.
     */
    public ConversationSummary acceptRequest(String conversationId, String userId) {
        String user = user(userId);
        Conversation conversation = memberConversation(conversationId, user);
        if (conversation.getType() != ConversationType.DIRECT) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Only a direct conversation can be a request");
        }
        if (conversation.isPendingFor(user)) {
            conversations.acceptRequest(conversation.getConversationId(), user);
            conversation = conversation.accepted();
        }
        return summary(conversation, user);
    }

    public ConversationSummary conversation(String conversationId, String userId) {
        String user = user(userId);
        return summary(memberConversation(conversationId, user), user);
    }

    /**
     * Marks the conversation read up to seq for the caller, on all of their devices. The marker never moves back,
     * and a seq past the newest message counts as the newest message. Returns the caller's read state after it.
     */
    public ReadState markRead(String conversationId, String userId, Long seq) {
        if (seq == null || seq < 0) {
            throw new Rejected(Rejected.Reason.VALIDATION, "seq must be 0 or more");
        }
        String user = user(userId);
        Conversation conversation = memberConversation(conversationId, user);
        String id = conversation.getConversationId();
        ReadMarkerRepository.Advance marker = readMarkers.advance(id, user, Math.min(seq, conversation.getLastSeq()), now());
        if (marker.moved()) {
            events.publishReadUpdated(id, user, marker.lastReadSeq());
        }
        return readMarkers.readStates(user, List.of(id)).get(id);
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
        Conversation conversation = allowedToWrite(memberConversation(conversationId, sender), sender);
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
     * Writes a SYSTEM message with no sender. Only server code calls this: clients cannot post SYSTEM.
     * Every member sees it through the normal fan-out; nobody is pushed. A system message is never deleted
     * or reacted to. conversationId can only be a group here, because DIRECT conversations are never created
     * with an id the caller picks: when the conversation is not stored yet, it is opened as the GROUP with that id.
     */
    @Override
    public Message postSystem(String conversationId, String text) {
        String id = uuid(conversationId, "conversationId");
        String problem = ContentValidator.problem(new MessageContent.System(text));
        if (problem != null) {
            throw new Rejected(Rejected.Reason.VALIDATION, problem);
        }
        Conversation conversation = conversations.findById(id);
        if (conversation == null) {
            try {
                conversation = openGroup(id, List.of());
            } catch (Rejected rejected) {
                // Someone opened it at the same moment: write into theirs.
                conversation = conversations.findById(id);
                if (rejected.getReason() != Rejected.Reason.CONFLICT || conversation == null) {
                    throw rejected;
                }
            }
        }
        return store(conversation, null, UUID.randomUUID().toString(), new MessageContent.System(text), null, null, null)
            .getMessage();
    }

    /**
     * Forwards a message the sender can see into another conversation. forwardedFrom always points at the first original.
     */
    public Sent forward(String conversationId, String senderId, String clientMsgId, String sourceMessageId) {
        String clientId = uuid(clientMsgId, "clientMsgId");
        String sender = user(senderId);
        Conversation target = allowedToWrite(memberConversation(conversationId, sender), sender);
        Message existing = messages.findBySenderAndClientMsgId(sender, clientId);
        if (existing != null) {
            return new Sent(existing, false);
        }
        Message source = visibleMessage(sourceMessageId, sender);
        if (source.isDeleted()) {
            throw new Rejected(Rejected.Reason.VALIDATION, "A deleted message cannot be forwarded");
        }
        if (source.getType() == MessageType.SYSTEM) {
            throw new Rejected(Rejected.Reason.VALIDATION, "A system message cannot be forwarded");
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
        return messages(conversationId, userId, afterSeq, beforeSeq, limit, null);
    }

    /**
     * With text, only live TEXT messages containing it (ignoring case) are returned, paged the same way.
     * Blank text means no search.
     */
    public MessagePage messages(
        String conversationId, String userId, Long afterSeq, Long beforeSeq, Integer limit, String text) {
        if (afterSeq != null && beforeSeq != null) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Use afterSeq or beforeSeq, not both");
        }
        if (afterSeq != null && afterSeq < 0) {
            throw new Rejected(Rejected.Reason.VALIDATION, "afterSeq must be 0 or more");
        }
        if (beforeSeq != null && beforeSeq < 1) {
            throw new Rejected(Rejected.Reason.VALIDATION, "beforeSeq must be 1 or more");
        }
        String needle = text == null || text.isBlank() ? null : text.trim();
        if (needle != null && needle.length() > SEARCH_MAX_LENGTH) {
            throw new Rejected(Rejected.Reason.VALIDATION, "q must be at most " + SEARCH_MAX_LENGTH + " characters");
        }
        int size = limit(limit, MESSAGES_DEFAULT_LIMIT, MESSAGES_MAX_LIMIT);
        String id = memberConversation(conversationId, user(userId)).getConversationId();
        // One extra row tells whether another page exists without a count query.
        if (afterSeq != null) {
            List<Message> rows = needle == null
                ? messages.findAfterSeq(id, afterSeq, size + 1)
                : search.searchAfterSeq(id, afterSeq, needle, size + 1);
            boolean hasMore = rows.size() > size;
            return new MessagePage(hasMore ? rows.subList(0, size) : rows, hasMore);
        }
        long before = beforeSeq == null ? Long.MAX_VALUE : beforeSeq;
        List<Message> rows = needle == null
            ? messages.findBeforeSeq(id, before, size + 1)
            : search.searchBeforeSeq(id, before, needle, size + 1);
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
        if (!user.equals(message.getSenderId())) {
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
        if (message.getType() == MessageType.SYSTEM) {
            throw new Rejected(Rejected.Reason.VALIDATION, "A system message cannot get reactions");
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
        if (message == null && senderId == null) {
            throw new IllegalStateException("A system message was not stored: " + clientMsgId);
        }
        if (message == null) {
            // A concurrent retry with the same clientMsgId won. No seq was used for this attempt.
            return new Sent(messages.findBySenderAndClientMsgId(senderId, clientMsgId), false);
        }
        events.publishMessageCreated(message);
        if (senderId == null) {
            return new Sent(message, true);
        }
        // Writing in a conversation means having read it up to your own message.
        ReadMarkerRepository.Advance own = readMarkers.advance(message.getConversationId(), senderId, message.getSeq(), now);
        if (own.moved()) {
            events.publishReadUpdated(message.getConversationId(), senderId, own.lastReadSeq());
        }
        return new Sent(message, true);
    }

    /**
     * userId must already be canonical, see {@link #user(String)}.
     */
    private Conversation memberConversation(String conversationId, String userId) {
        Conversation conversation = conversations.findById(uuid(conversationId, "conversationId"));
        // Unknown and foreign conversations look the same, so ids cannot be probed.
        if (conversation == null || !members.isMember(conversation, userId)) {
            throw new Rejected(Rejected.Reason.FORBIDDEN, NOT_MEMBER);
        }
        return settled(conversation);
    }

    /**
     * A request ends by itself once the two users are friends: no accept needed.
     */
    private Conversation settled(Conversation conversation) {
        if (conversation.getPendingFor() == null) {
            return conversation;
        }
        String other = conversation.otherMember(conversation.getPendingFor());
        if (other != null && friendships.areFriends(other, conversation.getPendingFor())) {
            conversations.acceptRequest(conversation.getConversationId(), conversation.getPendingFor());
            return conversation.accepted();
        }
        return conversation;
    }

    private boolean stillPending(Conversation conversation, String userId) {
        return settled(conversation).isPendingFor(userId);
    }

    /**
     * Writing into a DIRECT conversation: refused when either user blocked the other. The recipient of a request
     * who writes back has accepted it.
     */
    private Conversation allowedToWrite(Conversation conversation, String senderId) {
        if (conversation.getType() != ConversationType.DIRECT) {
            return conversation;
        }
        String other = conversation.otherMember(senderId);
        if (other != null && blocks.isBlocked(senderId, other)) {
            throw new Rejected(Rejected.Reason.FORBIDDEN, BLOCKED);
        }
        if (conversation.isPendingFor(senderId)) {
            conversations.acceptRequest(conversation.getConversationId(), senderId);
            return conversation.accepted();
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

    /**
     * userId is the caller, for the read state; null leaves the read state out.
     */
    private ConversationSummary summary(Conversation conversation, String userId) {
        String id = conversation.getConversationId();
        Map<String, Message> latest = messages.findLatest(List.of(id));
        ReadState read = userId == null ? null : readMarkers.readStates(userId, List.of(id)).get(id);
        ConversationPrefs own = userId == null ? null : prefs.find(userId, List.of(id)).getOrDefault(id, ConversationPrefs.DEFAULT);
        return new ConversationSummary(conversation, members.memberIds(conversation), latest.get(id), read, own,
            userId != null && conversation.isPendingFor(userId));
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
        boolean pinned = value.startsWith("p");
        String rest = pinned ? value.substring(1) : value;
        int split = rest.indexOf('_');
        String id = split < 0 ? null : Ids.canonical(rest.substring(split + 1));
        try {
            if (id != null) {
                return new ConversationCursor(pinned, Instant.ofEpochMilli(Long.parseLong(rest.substring(0, split))), id);
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

    public record PrefsChange(String conversationId, ConversationPrefs prefs) {
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
