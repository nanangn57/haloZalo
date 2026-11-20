package push;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uses Expo push HTTP API. Tokens that are not Expo still get logged so local demos stay visible.
 */
@Component
public final class ExpoPushNotifier implements PushNotifier {
    private static final Logger log = LoggerFactory.getLogger(ExpoPushNotifier.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final DeviceTokenRepository tokens;
    private final HttpClient http;
    private final String endpoint;
    private final boolean enabled;

    public ExpoPushNotifier(
        DeviceTokenRepository tokens,
        @Value("${messaging.push.expo-url:https://exp.host/--/api/v2/push/send}") String endpoint,
        @Value("${messaging.push.enabled:true}") boolean enabled
    ) {
        this.tokens = tokens;
        this.endpoint = endpoint;
        this.enabled = enabled;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public void notifyNewMessage(Collection<String> offlineUserIds, String conversationId, String preview) {
        if (!enabled || offlineUserIds == null || offlineUserIds.isEmpty()) {
            return;
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        for (String userId : offlineUserIds) {
            for (String token : tokens.tokensOf(userId)) {
                if (token.startsWith("ExponentPushToken") || token.startsWith("ExpoPushToken")) {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("to", token);
                    body.put("title", "New message");
                    body.put("body", preview == null || preview.isBlank() ? "You have a new message" : preview);
                    body.put("data", Map.of("conversationId", conversationId == null ? "" : conversationId));
                    messages.add(body);
                } else {
                    log.info("Offline push for user {} token={} conversation={}", userId, abbreviate(token), conversationId);
                }
            }
        }
        if (messages.isEmpty()) {
            return;
        }
        try {
            String payload = JSON.writeValueAsString(messages);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("Expo push failed status={} body={}", response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.warn("Expo push failed: {}", ex.toString());
        }
    }

    private static String abbreviate(String token) {
        if (token == null) {
            return "";
        }
        return token.length() <= 12 ? token : token.substring(0, 8) + "…";
    }
}
