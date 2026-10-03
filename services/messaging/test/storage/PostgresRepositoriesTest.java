package storage;

import conversation.Conversation;
import conversation.ConversationCursor;
import conversation.ConversationType;
import message.Message;
import message.MessageContent;
import message.MessageMetadata;
import message.MessageStatus;
import reaction.MessageReaction;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real SQL against a real PostgreSQL, schema created by the Flyway migrations. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresRepositoriesTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final String ALICE = UUID.randomUUID().toString();
    private static final String BOB = UUID.randomUUID().toString();

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static PostgresConversationRepository conversations;
    private static PostgresMessageRepository messages;
    private static PostgresReactionRepository reactions;

    private String conversationId;

    @BeforeAll
    static void migrate() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setMaximumPoolSize(10);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        conversations = new PostgresConversationRepository(jdbc, transaction);
        messages = new PostgresMessageRepository(jdbc, transaction);
        reactions = new PostgresReactionRepository(jdbc);
    }

    @AfterAll
    static void close() {
        dataSource.close();
    }

    @BeforeEach
    void cleanTables() {
        jdbc.execute("TRUNCATE message_reactions, messages, conversation_members, conversations");
        conversationId = UUID.randomUUID().toString();
        assertTrue(conversations.insert(new Conversation(conversationId, ConversationType.DIRECT, Set.of(ALICE, BOB), 0, NOW)));
    }

    @Test
    void conversationKeepsItsMembersAndRejectsASecondInsertWithTheSameId() {
        Conversation stored = conversations.findById(conversationId);

        assertEquals(ConversationType.DIRECT, stored.getType());
        assertEquals(Set.of(ALICE, BOB), stored.getMemberIds());
        assertEquals(NOW, stored.getCreatedAt());
        assertFalse(conversations.insert(new Conversation(conversationId, ConversationType.GROUP, Set.of(ALICE), 0, NOW)));
        assertNull(conversations.findById(UUID.randomUUID().toString()));
    }

    @Test
    void appendGivesTheNextSeqAndADuplicateClientMsgIdUsesNoSeq() {
        String clientMsgId = UUID.randomUUID().toString();

        Message first = messages.append(draft(ALICE, clientMsgId, new MessageContent.Text("one")));
        Message duplicate = messages.append(draft(ALICE, clientMsgId, new MessageContent.Text("one again")));
        Message second = messages.append(draft(BOB, UUID.randomUUID().toString(), new MessageContent.Text("two")));

        assertEquals(1, first.getSeq());
        assertNull(duplicate);
        assertEquals(2, second.getSeq());
        assertEquals(2L, conversations.findById(conversationId).getLastSeq());
        assertEquals(first.getMessageId(), messages.findBySenderAndClientMsgId(ALICE, clientMsgId).getMessageId());
    }

    @Test
    void parallelSendsGetEverySeqOnceWithNoGaps() throws Exception {
        List<Callable<Message>> sends = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String sender = i % 2 == 0 ? ALICE : BOB;
            sends.add(() -> messages.append(draft(sender, UUID.randomUUID().toString(), new MessageContent.Text("hi"))));
        }

        List<Long> seqs = run(sends).stream().map(Message::getSeq).sorted().toList();

        assertEquals(LongStream.rangeClosed(1, 100).boxed().toList(), seqs);
    }

    @Test
    void parallelRetriesOfOneMessageStoreItOnceAndLeaveNoGap() throws Exception {
        String clientMsgId = UUID.randomUUID().toString();
        List<Callable<Message>> retries = Collections.nCopies(10,
            () -> messages.append(draft(ALICE, clientMsgId, new MessageContent.Text("hi"))));

        List<Message> stored = run(retries).stream().filter(Objects::nonNull).toList();
        Message next = messages.append(draft(ALICE, UUID.randomUUID().toString(), new MessageContent.Text("next")));

        assertEquals(1, stored.size());
        assertEquals(1, stored.get(0).getSeq());
        assertEquals(2, next.getSeq());
    }

    @Test
    void everyContentTypeAndTheOptionalFieldsSurviveTheRoundTrip() {
        List<MessageContent> contents = List.of(
            new MessageContent.Text("hello \"world\" ạ"),
            new MessageContent.Emotion("love"),
            new MessageContent.Image("https://cdn/a.png", null, 640, 480, "cat"),
            new MessageContent.Video("https://cdn/a.mp4", "https://cdn/a.jpg", 1280, 720, 30, null),
            new MessageContent.Document("https://cdn/a.pdf", "a.pdf", "application/pdf", 2048L));
        Message original = messages.append(draft(ALICE, UUID.randomUUID().toString(), contents.get(0)));
        for (MessageContent content : contents) {
            Message stored = messages.append(new Message(UUID.randomUUID().toString(), conversationId, 0, BOB,
                UUID.randomUUID().toString(), content, new MessageMetadata(Map.of("clientVersion", "1.2")),
                original.getMessageId(), original.getMessageId(), MessageStatus.SENT, NOW, NOW, null));

            Message read = messages.findById(stored.getMessageId());

            assertEquals(content, read.getContent());
            assertEquals(Map.of("clientVersion", "1.2"), read.getMetadata().getValues());
            assertEquals(original.getMessageId(), read.getReplyTo());
            assertEquals(original.getMessageId(), read.getForwardedFrom());
            assertEquals(NOW, read.getCreatedAt());
        }
        String imageJson = jdbc.queryForObject("SELECT content::text FROM messages WHERE type = 'IMAGE'", String.class);
        assertFalse(imageJson.contains("type"), imageJson);
        assertFalse(imageJson.contains("thumbnailUrl"), imageJson);
    }

    @Test
    void markDeletedKeepsTheRowAndCatchUpPagesInSeqOrder() {
        Message gone = messages.append(draft(ALICE, UUID.randomUUID().toString(), new MessageContent.Text("oops")));
        for (int i = 0; i < 4; i++) {
            messages.append(draft(BOB, UUID.randomUUID().toString(), new MessageContent.Text("m" + i)));
        }

        messages.markDeleted(gone.softDeleted(NOW.plusSeconds(5)));

        Message read = messages.findById(gone.getMessageId());
        assertEquals(MessageStatus.DELETED, read.getStatus());
        assertEquals(NOW.plusSeconds(5), read.getDeletedAt());
        assertEquals(List.of(2L, 3L), messages.findAfterSeq(conversationId, 1, 2).stream().map(Message::getSeq).toList());
        assertEquals(List.of(), messages.findAfterSeq(conversationId, 5, 10));
    }

    @Test
    void reactionsAreOncePerUserAndCode() {
        Message message = messages.append(draft(ALICE, UUID.randomUUID().toString(), new MessageContent.Text("party")));

        assertTrue(reactions.insert(reaction(message, BOB, "love")));
        assertFalse(reactions.insert(reaction(message, BOB, "love")));
        assertTrue(reactions.insert(reaction(message, ALICE, "love")));
        assertTrue(reactions.delete(message.getMessageId(), BOB, "love"));
        assertFalse(reactions.delete(message.getMessageId(), BOB, "love"));

        List<MessageReaction> left = reactions.findByMessageId(message.getMessageId());
        assertEquals(1, left.size());
        assertEquals(ALICE, left.get(0).getUserId());
        assertNotNull(left.get(0).getCreatedAt());
    }

    @Test
    void oneDirectConversationPerPairEvenWhenBothOpenItAtOnce() throws Exception {
        String carol = UUID.randomUUID().toString();
        String dave = UUID.randomUUID().toString();
        List<Callable<Boolean>> opens = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Set<String> pair = i % 2 == 0 ? Set.of(carol, dave) : Set.of(dave, carol);
            opens.add(() -> conversations.insert(
                new Conversation(UUID.randomUUID().toString(), ConversationType.DIRECT, pair, 0, NOW)));
        }

        long inserted = runAll(opens).stream().filter(Boolean::booleanValue).count();

        assertEquals(1, inserted);
        Conversation found = conversations.findDirect(Conversation.directKey(Set.of(carol, dave)));
        assertEquals(Set.of(carol, dave), found.getMemberIds());
        assertEquals(conversationId, conversations.findDirect(Conversation.directKey(Set.of(ALICE, BOB))).getConversationId());
    }

    @Test
    void conversationListIsNewestActivityFirstAndPagesWithTheCursor() {
        String quiet = UUID.randomUUID().toString();
        String busy = UUID.randomUUID().toString();
        conversations.insert(new Conversation(quiet, ConversationType.GROUP, Set.of(ALICE, BOB, UUID.randomUUID().toString()), 0, NOW.plusSeconds(10)));
        conversations.insert(new Conversation(busy, ConversationType.GROUP, Set.of(ALICE, UUID.randomUUID().toString(), UUID.randomUUID().toString()), 0, NOW));
        messages.append(draftIn(busy, NOW.plusSeconds(20)));
        Message newest = messages.append(draftIn(busy, NOW.plusSeconds(30)));

        List<Conversation> all = conversations.findForMember(ALICE, null, 10);
        List<Conversation> firstPage = conversations.findForMember(ALICE, null, 2);
        List<Conversation> rest = conversations.findForMember(ALICE, ConversationCursor.after(firstPage.get(1)), 2);

        assertEquals(List.of(busy, quiet, conversationId), all.stream().map(Conversation::getConversationId).toList());
        assertEquals(NOW.plusSeconds(30), all.get(0).getLastMessageAt());
        assertEquals(2L, all.get(0).getLastSeq());
        assertEquals(3, all.get(0).getMemberIds().size());
        assertEquals(List.of(conversationId), rest.stream().map(Conversation::getConversationId).toList());
        assertEquals(List.of(quiet), conversations.findForMember(BOB, null, 10).stream()
            .map(Conversation::getConversationId).filter(id -> !id.equals(conversationId)).toList());

        Map<String, Message> latest = messages.findLatest(List.of(busy, quiet, conversationId));
        assertEquals(Set.of(busy), latest.keySet());
        assertEquals(newest.getMessageId(), latest.get(busy).getMessageId());
    }

    @Test
    void historyTakesTheNewestPageBeforeASeqInSeqOrder() {
        for (int i = 0; i < 5; i++) {
            messages.append(draft(ALICE, UUID.randomUUID().toString(), new MessageContent.Text("m" + i)));
        }

        assertEquals(List.of(4L, 5L), seqs(messages.findBeforeSeq(conversationId, Long.MAX_VALUE, 2)));
        assertEquals(List.of(2L, 3L), seqs(messages.findBeforeSeq(conversationId, 4, 2)));
        assertEquals(List.of(1L), seqs(messages.findBeforeSeq(conversationId, 2, 2)));
    }

    private Message draftIn(String conversation, Instant at) {
        return new Message(UUID.randomUUID().toString(), conversation, 0, ALICE, UUID.randomUUID().toString(),
            new MessageContent.Text("hi"), null, null, null, MessageStatus.SENT, at, at, null);
    }

    private static List<Long> seqs(List<Message> rows) {
        return rows.stream().map(Message::getSeq).toList();
    }

    private static <T> List<T> runAll(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<T> results = new ArrayList<>();
            for (Future<T> result : pool.invokeAll(tasks)) {
                results.add(result.get());
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    private Message draft(String sender, String clientMsgId, MessageContent content) {
        return new Message(UUID.randomUUID().toString(), conversationId, 0, sender, clientMsgId, content, null,
            null, null, MessageStatus.SENT, NOW, NOW, null);
    }

    private static MessageReaction reaction(Message message, String userId, String code) {
        return new MessageReaction(UUID.randomUUID().toString(), message.getMessageId(), userId, code, NOW);
    }

    private static List<Message> run(List<Callable<Message>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Message> results = new ArrayList<>();
            for (Future<Message> result : pool.invokeAll(tasks)) {
                results.add(result.get());
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }
}
