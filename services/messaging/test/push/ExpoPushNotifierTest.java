package push;

import storage.MemoryNotificationPrefs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Talks to a local HTTP server standing in for Expo.
 */
class ExpoPushNotifierTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";
    private static final String CONVERSATION = "cccccccc-0000-4000-8000-000000000001";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    private HttpServer expo;
    private MemoryNotificationPrefs prefs;
    private ExpoPushNotifier notifier;

    @BeforeEach
    void setUp() throws Exception {
        expo = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        expo.createContext("/push", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        expo.start();
        prefs = new MemoryNotificationPrefs();
        DeviceTokenRepository tokens = new FixedTokens();
        notifier = new ExpoPushNotifier(tokens, prefs, "http://127.0.0.1:" + expo.getAddress().getPort() + "/push", true);
    }

    @AfterEach
    void tearDown() {
        expo.stop(0);
    }

    @Test
    void pushCarriesThePreviewByDefault() throws Exception {
        notifier.notifyNewMessage(List.of(ALICE), CONVERSATION, "hello there");

        JsonNode sent = onlyRequest();
        assertEquals(1, sent.size());
        assertEquals("ExponentPushToken[alice]", sent.get(0).get("to").asText());
        assertEquals("hello there", sent.get(0).get("body").asText());
        assertEquals(CONVERSATION, sent.get(0).get("data").get("conversationId").asText());
    }

    @Test
    void aUserWithPushOffGetsNothingWhileOthersStillDo() throws Exception {
        prefs.update(ALICE, false, null, Instant.now());

        notifier.notifyNewMessage(List.of(ALICE, BOB), CONVERSATION, "hello there");

        JsonNode sent = onlyRequest();
        assertEquals(1, sent.size());
        assertEquals("ExponentPushToken[bob]", sent.get(0).get("to").asText());
    }

    @Test
    void noRequestAtAllWhenEveryoneTurnedPushOff() {
        prefs.update(ALICE, false, null, Instant.now());

        notifier.notifyNewMessage(List.of(ALICE), CONVERSATION, "hello there");

        assertTrue(requests.isEmpty());
    }

    @Test
    void previewOffSendsAGenericBodyOnlyToThatUser() throws Exception {
        prefs.update(ALICE, null, false, Instant.now());

        notifier.notifyNewMessage(List.of(ALICE, BOB), CONVERSATION, "secret plan");

        JsonNode sent = onlyRequest();
        assertEquals(2, sent.size());
        for (JsonNode message : sent) {
            String body = message.get("body").asText();
            if (message.get("to").asText().contains("alice")) {
                assertEquals("You have a new message", body);
            } else {
                assertEquals("secret plan", body);
            }
        }
    }

    @Test
    void previewOffWithPushOffStillSendsNothing() {
        prefs.update(ALICE, false, false, Instant.now());

        notifier.notifyNewMessage(List.of(ALICE), CONVERSATION, "secret plan");

        assertTrue(requests.isEmpty());
    }

    private JsonNode onlyRequest() throws Exception {
        assertEquals(1, requests.size());
        return JSON.readTree(requests.get(0));
    }

    private static final class FixedTokens implements DeviceTokenRepository {
        @Override
        public void upsert(String userId, String token, String platform) {
        }

        @Override
        public void delete(String userId, String token) {
        }

        @Override
        public List<String> tokensOf(String userId) {
            return List.of(userId.equals(ALICE) ? "ExponentPushToken[alice]" : "ExponentPushToken[bob]");
        }
    }
}
