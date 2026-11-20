package realtime;

import conversation.Conversation;
import member.ConversationMembers;
import storage.ConversationRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ephemeral client signals on /ws: typing and presence. Not persisted; fan-out to conversation members only.
 */
@Component
public final class ClientSignalRelay {
    public static final String TYPING = "messaging.typing";
    public static final String PRESENCE = "messaging.presence";

    private static final Logger log = LoggerFactory.getLogger(ClientSignalRelay.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ConversationRepository conversations;
    private final ConversationMembers members;
    private final RealtimeBus realtime;

    public ClientSignalRelay(
        ConversationRepository conversations,
        ConversationMembers members,
        RealtimeBus realtime
    ) {
        this.conversations = conversations;
        this.members = members;
        this.realtime = realtime;
    }

    public void handle(String userId, String raw) {
        if (userId == null || userId.isBlank() || raw == null || raw.isBlank()) {
            return;
        }
        try {
            JsonNode root = JSON.readTree(raw);
            String type = text(root, "type");
            JsonNode payload = root.has("payload") ? root.get("payload") : root;
            String conversationId = text(payload, "conversationId");
            if (conversationId == null || conversationId.isBlank()) {
                return;
            }
            if (TYPING.equals(type) || "client.typing".equals(type)) {
                boolean typing = payload.has("typing") && payload.get("typing").asBoolean(false);
                fanOut(conversationId, userId, TYPING, Map.of(
                    "conversationId", conversationId,
                    "userId", userId,
                    "typing", typing
                ));
                return;
            }
            if (PRESENCE.equals(type) || "client.presence".equals(type)) {
                String status = text(payload, "status");
                if (status == null || status.isBlank()) {
                    status = "online";
                }
                fanOut(conversationId, userId, PRESENCE, Map.of(
                    "conversationId", conversationId,
                    "userId", userId,
                    "status", status
                ));
            }
        } catch (Exception ex) {
            log.debug("Ignored client signal from {}: {}", userId, ex.toString());
        }
    }

    private void fanOut(String conversationId, String senderId, String type, Map<String, Object> payload) {
        Conversation conversation = conversations.findById(conversationId);
        if (conversation == null || !members.isMember(conversation, senderId)) {
            return;
        }
        List<String> recipients = new ArrayList<>();
        for (String memberId : members.memberIds(conversation)) {
            if (!memberId.equals(senderId)) {
                recipients.add(memberId);
            }
        }
        if (recipients.isEmpty()) {
            return;
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("type", type);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("producer", "messaging");
        envelope.put("payload", payload);
        try {
            realtime.publish(new RealtimeFrame(recipients, JSON.writeValueAsString(envelope)));
        } catch (Exception ex) {
            log.warn("Could not fan-out {} for conversation {}", type, conversationId, ex);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }
}
