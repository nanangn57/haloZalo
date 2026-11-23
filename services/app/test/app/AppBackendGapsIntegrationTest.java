package app;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole app on a real PostgreSQL: identity changes a group, messaging writes the SYSTEM message, a socket
 * makes a user online, and notification prefs are stored. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = HaloZaloApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "identity.registration.activation=immediate",
        "messaging.push.enabled=false"
    })
@ActiveProfiles("app")
class AppBackendGapsIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("halozalo");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Test
    void groupChangesBecomeSystemMessagesForMembers() {
        Account alice = register("alice");
        Account bob = register("bob");
        Account carol = register("carol");

        String groupId = JsonPath.read(call(HttpMethod.POST, "/groups", alice.token, "{\"name\":\"team\"}").getBody(),
            "$.groupId");
        assertEquals(201, call(HttpMethod.POST, "/conversations/group", alice.token,
            "{\"groupId\":\"" + groupId + "\"}").getStatusCode().value());

        assertEquals(201, call(HttpMethod.POST, "/groups/" + groupId + "/members", alice.token,
            "{\"userId\":\"" + bob.userId + "\"}").getStatusCode().value());
        call(HttpMethod.POST, "/groups/" + groupId + "/members", alice.token, "{\"userId\":\"" + carol.userId + "\"}");
        call(HttpMethod.DELETE, "/groups/" + groupId + "/members/" + carol.userId, alice.token, null);
        call(HttpMethod.POST, "/groups/" + groupId + "/transfer-owner", alice.token,
            "{\"userId\":\"" + bob.userId + "\"}");
        call(HttpMethod.DELETE, "/groups/" + groupId + "/members/" + alice.userId, alice.token, null);

        ResponseEntity<String> history = call(HttpMethod.GET, "/conversations/" + groupId + "/messages", bob.token, null);
        assertEquals(200, history.getStatusCode().value());
        List<String> types = JsonPath.read(history.getBody(), "$.messages[*].type");
        List<String> bodies = JsonPath.read(history.getBody(), "$.messages[*].body");
        assertEquals(List.of("SYSTEM", "SYSTEM", "SYSTEM", "SYSTEM", "SYSTEM"), types);
        assertEquals(List.of(
            "alice added bob",
            "alice added carol",
            "alice removed carol",
            "alice made bob the group owner",
            "alice left the group"), bodies.stream().map(String::toLowerCase).toList());
        assertEquals(0, ((List<?>) JsonPath.read(history.getBody(), "$.messages[?(@.senderId)]")).size());
        assertEquals(List.of(1, 2, 3, 4, 5), JsonPath.read(history.getBody(), "$.messages[*].seq"));
    }

    @Test
    void aClientCannotPostSystem() {
        Account alice = register("sysclient");
        Account bob = register("sysclient2");
        String conversationId = JsonPath.read(call(HttpMethod.POST, "/conversations/direct", alice.token,
            "{\"userId\":\"" + bob.userId + "\"}").getBody(), "$.conversationId");

        ResponseEntity<String> posted = call(HttpMethod.POST, "/conversations/" + conversationId + "/messages",
            alice.token, "{\"type\":\"SYSTEM\",\"body\":\"hi\",\"clientMsgId\":\"" + UUID.randomUUID() + "\"}");

        assertEquals(400, posted.getStatusCode().value());
    }

    @Test
    void presenceFollowsOpenSockets() throws Exception {
        Account alice = register("presenta");
        Account bob = register("presentb");
        String ids = alice.userId + "," + bob.userId;

        assertEquals(401, call(HttpMethod.GET, "/presence?ids=" + ids, null, null).getStatusCode().value());
        ResponseEntity<String> before = call(HttpMethod.GET, "/presence?ids=" + ids, alice.token, null);
        assertEquals(200, before.getStatusCode().value());
        assertFalse((Boolean) JsonPath.read(before.getBody(), "$.presences[1].online"));
        assertNull(((List<?>) JsonPath.read(before.getBody(), "$.presences[?(@.userId=='" + bob.userId + "')].lastSeenAt")).stream()
            .findFirst().orElse(null));

        WebSocketSession socket = new StandardWebSocketClient().execute(new TextWebSocketHandler(),
            null, URI.create("ws://localhost:" + port + "/ws?access_token=" + bob.token)).get();
        try {
            awaitOnline(alice, bob, true);
        } finally {
            socket.close();
        }
        awaitOnline(alice, bob, false);
        ResponseEntity<String> after = call(HttpMethod.GET, "/presence?ids=" + bob.userId, alice.token, null);
        assertTrue(((String) JsonPath.read(after.getBody(), "$.presences[0].lastSeenAt")).endsWith("Z"));
    }

    @Test
    void notificationPrefsAreStoredPerUser() {
        Account alice = register("notifa");
        Account bob = register("notifb");

        assertEquals(401, call(HttpMethod.GET, "/me/notifications", null, null).getStatusCode().value());
        ResponseEntity<String> defaults = call(HttpMethod.GET, "/me/notifications", alice.token, null);
        assertEquals(true, JsonPath.read(defaults.getBody(), "$.pushEnabled"));
        assertEquals(true, JsonPath.read(defaults.getBody(), "$.previewEnabled"));

        ResponseEntity<String> patched = call(HttpMethod.PATCH, "/me/notifications", alice.token,
            "{\"previewEnabled\":false}");
        assertEquals(200, patched.getStatusCode().value());
        assertEquals(true, JsonPath.read(patched.getBody(), "$.pushEnabled"));
        assertEquals(false, JsonPath.read(patched.getBody(), "$.previewEnabled"));
        assertEquals(400, call(HttpMethod.PATCH, "/me/notifications", alice.token, "{}").getStatusCode().value());

        assertEquals(false, JsonPath.read(call(HttpMethod.GET, "/me/notifications", alice.token, null).getBody(),
            "$.previewEnabled"));
        assertEquals(true, JsonPath.read(call(HttpMethod.GET, "/me/notifications", bob.token, null).getBody(),
            "$.previewEnabled"));
    }

    private void awaitOnline(Account asker, Account user, boolean expected) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            ResponseEntity<String> response = call(HttpMethod.GET, "/presence?ids=" + user.userId, asker.token, null);
            if (expected == (Boolean) JsonPath.read(response.getBody(), "$.presences[0].online")) {
                return;
            }
            Thread.sleep(100);
        }
        assertEquals(expected, !expected, "presence never became " + expected);
    }

    private Account register(String name) {
        String email = name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        ResponseEntity<String> registered = call(HttpMethod.POST, "/auth/register", null,
            "{\"email\":\"" + email + "\",\"password\":\"password123\",\"displayName\":\"" + name + "\"}");
        int registerStatus = registered.getStatusCode().value();
        assertTrue(registerStatus == 201 || registerStatus == 202, registered.getBody());
        ResponseEntity<String> login = call(HttpMethod.POST, "/auth/login", null,
            "{\"login\":\"" + email + "\",\"password\":\"password123\"}");
        assertEquals(200, login.getStatusCode().value(), login.getBody());
        return new Account(JsonPath.read(login.getBody(), "$.user.userId"), JsonPath.read(login.getBody(), "$.accessToken"));
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private record Account(String userId, String token) {
    }
}
