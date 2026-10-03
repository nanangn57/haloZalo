package app;

import conversation.ConversationType;
import http.MessageController;
import realtime.RealtimeSessions;
import service.MessageService;
import storage.MemoryConversations;
import storage.MemoryMessages;
import storage.MemoryReactions;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real server on a random port, real WebSocket clients, one instance with the local bus.
 */
@SpringBootTest(
    classes = RealtimeIntegrationTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "messaging.realtime.bus=local")
class RealtimeIntegrationTest {
    // New users for every test: the app is shared, and a pair of users has only one DIRECT conversation.
    private final String aliceId = UUID.randomUUID().toString();
    private final String bobId = UUID.randomUUID().toString();
    private final String carolId = UUID.randomUUID().toString();

    @LocalServerPort
    private int port;

    @Autowired
    private MessageService service;

    @Autowired
    private RealtimeSessions sessions;

    @Autowired
    private TestRestTemplate http;

    private final List<WebSocketSession> open = new ArrayList<>();
    private String conversationId;

    @BeforeEach
    void setUp() {
        conversationId = UUID.randomUUID().toString();
        service.openConversation(conversationId, ConversationType.DIRECT, List.of(aliceId, bobId));
    }

    @AfterEach
    void tearDown() throws Exception {
        for (WebSocketSession session : open) {
            if (session.isOpen()) {
                session.close();
            }
        }
    }

    @Test
    void everyDeviceOfEveryMemberGetsTheEnvelopeAndOutsidersGetNothing() throws Exception {
        BlockingQueue<String> alice = connect(aliceId);
        BlockingQueue<String> bobWeb = connect(bobId);
        BlockingQueue<String> bobMobile = connect(bobId);
        BlockingQueue<String> carol = connect(carolId);
        awaitSockets(aliceId, 1);
        awaitSockets(bobId, 2);

        String clientMsgId = UUID.randomUUID().toString();
        ResponseEntity<String> sent = post("/conversations/" + conversationId + "/messages", aliceId,
            "{\"type\":\"TEXT\",\"body\":\"hello\",\"clientMsgId\":\"" + clientMsgId + "\"}");
        assertEquals(201, sent.getStatusCode().value());
        String messageId = JsonPath.read(sent.getBody(), "$.messageId");

        for (BlockingQueue<String> device : List.of(alice, bobWeb, bobMobile)) {
            String frame = next(device);
            assertEquals("messaging.message.created", JsonPath.read(frame, "$.type"));
            assertEquals("messaging", JsonPath.read(frame, "$.producer"));
            assertEquals(messageId, JsonPath.read(frame, "$.payload.messageId"));
            assertEquals(1, (int) JsonPath.read(frame, "$.payload.seq"));
            assertEquals(clientMsgId, JsonPath.read(frame, "$.payload.clientMsgId"));
        }
        assertNull(carol.poll(300, TimeUnit.MILLISECONDS));

        // A retry with the same clientMsgId stores nothing new, so nothing is pushed.
        post("/conversations/" + conversationId + "/messages", aliceId,
            "{\"type\":\"TEXT\",\"body\":\"hello\",\"clientMsgId\":\"" + clientMsgId + "\"}");
        assertNull(bobWeb.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void reactionsAndDeletesArePushedToTheOtherDevices() throws Exception {
        String messageId = JsonPath.read(post("/conversations/" + conversationId + "/messages", aliceId,
            "{\"type\":\"TEXT\",\"body\":\"hi\",\"clientMsgId\":\"" + UUID.randomUUID() + "\"}").getBody(), "$.messageId");
        BlockingQueue<String> aliceMobile = connect(aliceId);
        awaitSockets(aliceId, 1);

        exchange(HttpMethod.PUT, "/conversations/" + conversationId + "/messages/" + messageId + "/reactions/love", bobId);
        String added = next(aliceMobile);
        assertEquals("messaging.reaction.added", JsonPath.read(added, "$.type"));
        assertEquals(bobId, JsonPath.read(added, "$.payload.userId"));
        assertEquals("love", JsonPath.read(added, "$.payload.code"));

        exchange(HttpMethod.DELETE, "/conversations/" + conversationId + "/messages/" + messageId, aliceId);
        String deleted = next(aliceMobile);
        assertEquals("messaging.message.deleted", JsonPath.read(deleted, "$.type"));
        assertEquals(messageId, JsonPath.read(deleted, "$.payload.messageId"));
        assertNotNull(JsonPath.read(deleted, "$.payload.deletedAt"));
    }

    @Test
    void upgradeWithoutUserIsRefused() {
        ExecutionException refused = assertThrows(ExecutionException.class, () -> new StandardWebSocketClient()
            .execute(new TextWebSocketHandler(), new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/ws"))
            .get(5, TimeUnit.SECONDS));

        assertTrue(String.valueOf(refused.getCause().getMessage()).contains("401"), refused.getCause().getMessage());
    }

    @Test
    void aClosedDeviceIsForgottenWhileTheOtherStaysConnected() throws Exception {
        connect(bobId);
        connect(bobId);
        awaitSockets(bobId, 2);

        open.get(0).close(CloseStatus.NORMAL);

        awaitSockets(bobId, 1);
    }

    private BlockingQueue<String> connect(String userId) throws Exception {
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(MessageController.USER_HEADER, userId);
        WebSocketSession session = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                frames.add(message.getPayload());
            }
        }, headers, URI.create("ws://localhost:" + port + "/ws")).get(5, TimeUnit.SECONDS);
        open.add(session);
        return frames;
    }

    private void awaitSockets(String userId, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (sessions.count(userId) != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(expected, sessions.count(userId));
    }

    private static String next(BlockingQueue<String> frames) throws InterruptedException {
        String frame = frames.poll(5, TimeUnit.SECONDS);
        assertNotNull(frame, "No frame arrived");
        return frame;
    }

    private ResponseEntity<String> post(String path, String userId, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(MessageController.USER_HEADER, userId);
        return http.postForEntity(path, new HttpEntity<>(body, headers), String.class);
    }

    private void exchange(HttpMethod method, String path, String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(MessageController.USER_HEADER, userId);
        ResponseEntity<String> response = http.exchange(path, method, new HttpEntity<>(headers), String.class);
        assertEquals(204, response.getStatusCode().value());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
    })
    @ConfigurationPropertiesScan("realtime")
    @ComponentScan({"api", "event", "http", "realtime", "service"})
    static class TestApplication {
        @Bean
        MemoryConversations conversations() {
            return new MemoryConversations();
        }

        @Bean
        MemoryMessages messages(MemoryConversations conversations) {
            return new MemoryMessages(conversations);
        }

        @Bean
        MemoryReactions reactions() {
            return new MemoryReactions();
        }
    }
}
