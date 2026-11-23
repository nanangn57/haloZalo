package presence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceRegistryTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";

    private MovableClock clock;
    private PresenceRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new MovableClock(Instant.parse("2026-11-22T09:00:00Z"));
        registry = new PresenceRegistry(clock);
    }

    @Test
    void aUserNeverSeenIsOfflineWithoutLastSeen() {
        Presence presence = registry.of(List.of(ALICE)).get(0);

        assertEquals(ALICE, presence.userId());
        assertFalse(presence.online());
        assertNull(presence.lastSeenAt());
    }

    @Test
    void aUserWithAnOpenSocketIsOnline() {
        registry.connected(ALICE);

        assertTrue(registry.of(List.of(ALICE)).get(0).online());
        assertFalse(registry.of(List.of(BOB)).get(0).online());
    }

    @Test
    void closingTheLastSocketMakesTheUserOfflineWithTheTimeItClosed() {
        registry.connected(ALICE);
        clock.advance(Duration.ofMinutes(5));
        registry.disconnected(ALICE);

        Presence presence = registry.of(List.of(ALICE)).get(0);
        assertFalse(presence.online());
        assertEquals(Instant.parse("2026-11-22T09:05:00Z"), presence.lastSeenAt());
    }

    @Test
    void aUserStaysOnlineWhileAnotherSocketIsOpen() {
        registry.connected(ALICE);
        registry.connected(ALICE);
        registry.disconnected(ALICE);

        assertTrue(registry.of(List.of(ALICE)).get(0).online());

        registry.disconnected(ALICE);
        assertFalse(registry.of(List.of(ALICE)).get(0).online());
    }

    @Test
    void extraDisconnectsNeverGoNegative() {
        registry.disconnected(ALICE);
        registry.connected(ALICE);
        registry.disconnected(ALICE);
        registry.disconnected(ALICE);
        registry.connected(ALICE);

        assertTrue(registry.of(List.of(ALICE)).get(0).online());
    }

    @Test
    void answersInTheOrderAsked() {
        registry.connected(BOB);

        List<Presence> presences = registry.of(List.of(ALICE, BOB));

        assertEquals(List.of(ALICE, BOB), presences.stream().map(Presence::userId).toList());
        assertEquals(List.of(false, true), presences.stream().map(Presence::online).toList());
    }

    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
