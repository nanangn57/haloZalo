package app;

import http.CurrentUser;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The browser preflight the web client sends before GET /conversations with X-User-Id.
 * java.net.http is used because it lets the test set Origin, unlike HttpURLConnection.
 */
@SpringBootTest(
    classes = RealtimeIntegrationTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "messaging.user-header-enabled=true")
class CorsIntegrationTest {
    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void preflightFromTheViteDevServerIsAllowed() throws Exception {
        HttpResponse<String> response = preflight("http://localhost:5173");

        assertEquals(200, response.statusCode());
        assertEquals("http://localhost:5173",
            response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        String allowedHeaders = response.headers().firstValue("Access-Control-Allow-Headers").orElse("");
        assertTrue(allowedHeaders.toLowerCase().contains(CurrentUser.HEADER.toLowerCase()), allowedHeaders);
    }

    @Test
    void preflightFromAnUnknownOriginIsRefused() throws Exception {
        HttpResponse<String> response = preflight("http://evil.example");

        assertEquals(403, response.statusCode());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    private HttpResponse<String> preflight(String origin) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/conversations?limit=50"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", CurrentUser.HEADER)
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
