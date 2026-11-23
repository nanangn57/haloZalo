package presence;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Presence kept in this process: a user is online while at least one socket of theirs is open. It is lost on
 * restart and not shared between instances, which is enough while one process serves every socket.
 */
@Component
public final class PresenceRegistry implements PresenceTracker, PresenceLookup {
    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Autowired
    public PresenceRegistry() {
        this(Clock.systemUTC());
    }

    public PresenceRegistry(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void connected(String userId) {
        if (userId == null) {
            return;
        }
        entries.merge(userId, new Entry(1, clock.instant()),
            (old, ignored) -> new Entry(old.sockets() + 1, clock.instant()));
    }

    @Override
    public void disconnected(String userId) {
        if (userId == null) {
            return;
        }
        entries.computeIfPresent(userId,
            (id, old) -> new Entry(Math.max(0, old.sockets() - 1), clock.instant()));
    }

    @Override
    public List<Presence> of(Collection<String> userIds) {
        List<Presence> found = new ArrayList<>();
        for (String userId : userIds) {
            Entry entry = entries.get(userId);
            found.add(entry == null
                ? new Presence(userId, false, null)
                : new Presence(userId, entry.sockets() > 0, entry.lastSeenAt()));
        }
        return found;
    }

    private record Entry(int sockets, Instant lastSeenAt) {
    }
}
