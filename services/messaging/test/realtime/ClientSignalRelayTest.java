package realtime;

import conversation.Conversation;
import conversation.ConversationType;
import member.ConversationMembers;
import member.StandaloneGroupMembers;
import storage.MemoryConversations;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientSignalRelayTest {
    @Test
    void typingIsFannedOutToOtherMembersOnly() {
        StandaloneGroupMembers groups = new StandaloneGroupMembers();
        groups.replace("11111111-2222-4333-8444-555555555555", Set.of("alice", "bob", "carol"));
        MemoryConversations conversations = new MemoryConversations();
        conversations.insert(new Conversation(
            "11111111-2222-4333-8444-555555555555",
            ConversationType.GROUP,
            Set.of(),
            0,
            Instant.parse("2026-09-28T09:00:00Z")
        ));
        List<RealtimeFrame> pushed = new ArrayList<>();
        ClientSignalRelay relay = new ClientSignalRelay(
            conversations,
            new ConversationMembers(groups),
            pushed::add
        );

        relay.handle(
            "alice",
            "{\"type\":\"messaging.typing\",\"payload\":{\"conversationId\":\"11111111-2222-4333-8444-555555555555\",\"typing\":true}}"
        );

        assertEquals(1, pushed.size());
        assertEquals(Set.of("bob", "carol"), Set.copyOf(pushed.get(0).recipients()));
        assertTrue(pushed.get(0).envelope().contains("\"messaging.typing\""));
        assertTrue(pushed.get(0).envelope().contains("\"alice\""));
    }
}
