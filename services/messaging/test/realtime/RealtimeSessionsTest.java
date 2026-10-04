package realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeSessionsTest {
    private static final RealtimeProperties PROPERTIES = new RealtimeProperties(
        RealtimeProperties.Bus.LOCAL, List.of("*"), Duration.ofSeconds(25), Duration.ofSeconds(60),
        Duration.ofSeconds(10), DataSize.ofKilobytes(512), DataSize.ofKilobytes(8));

    private MovableClock clock;
    private RealtimeSessions sessions;

    @BeforeEach
    void setUp() {
        clock = new MovableClock(Instant.parse("2026-10-03T09:00:00Z"));
        sessions = new RealtimeSessions(PROPERTIES, clock);
    }

    @Test
    void deliverReachesEverySocketOfEachRecipientAndNobodyElse() throws Exception {
        WebSocketSession bobWeb = socket("bob-web");
        WebSocketSession bobMobile = socket("bob-mobile");
        WebSocketSession carol = socket("carol");
        sessions.open(bobWeb, "bob");
        sessions.open(bobMobile, "bob");
        sessions.open(carol, "carol");

        sessions.deliver(List.of("bob", "alice"), "{\"type\":\"x\"}");

        verify(bobWeb).sendMessage(new TextMessage("{\"type\":\"x\"}"));
        verify(bobMobile).sendMessage(new TextMessage("{\"type\":\"x\"}"));
        verify(carol, never()).sendMessage(any());
    }

    @Test
    void aSocketThatFailsToSendIsClosedAndForgotten() throws Exception {
        WebSocketSession broken = socket("broken");
        WebSocketSession healthy = socket("healthy");
        doThrow(new IOException("reset")).when(broken).sendMessage(any());
        sessions.open(broken, "bob");
        sessions.open(healthy, "bob");

        sessions.deliver(List.of("bob"), "{}");

        verify(broken).close(CloseStatus.SESSION_NOT_RELIABLE);
        verify(healthy).sendMessage(new TextMessage("{}"));
        assertEquals(1, sessions.count("bob"));
    }

    @Test
    void heartbeatPingsLiveSocketsAndClosesSilentOnes() throws Exception {
        WebSocketSession silent = socket("silent");
        WebSocketSession answering = socket("answering");
        sessions.open(silent, "bob");
        sessions.open(answering, "carol");

        clock.advance(Duration.ofSeconds(50));
        sessions.seen("answering");
        clock.advance(Duration.ofSeconds(20));
        sessions.beat();

        verify(silent).close(CloseStatus.SESSION_NOT_RELIABLE);
        verify(answering).sendMessage(any(PingMessage.class));
        assertEquals(0, sessions.count("bob"));
        assertEquals(1, sessions.count("carol"));
    }

    @Test
    void stopClosesEverySocketWithGoingAwaySoClientsReconnect() throws Exception {
        WebSocketSession web = socket("web");
        WebSocketSession mobile = socket("mobile");
        sessions.open(web, "bob");
        sessions.open(mobile, "bob");
        sessions.start();

        sessions.stop();

        verify(web).close(CloseStatus.GOING_AWAY);
        verify(mobile).close(CloseStatus.GOING_AWAY);
        assertEquals(0, sessions.count("bob"));
    }

    @Test
    void forgettingTheLastSocketOfAUserLeavesNothingBehind() {
        sessions.open(socket("only"), "bob");

        sessions.forget("only");
        sessions.forget("only");

        assertEquals(0, sessions.count("bob"));
    }

    private static WebSocketSession socket(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
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
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
