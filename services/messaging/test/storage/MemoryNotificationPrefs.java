package storage;

import push.NotificationPrefs;
import push.NotificationPrefsRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryNotificationPrefs implements NotificationPrefsRepository {
    private final Map<String, NotificationPrefs> rows = new ConcurrentHashMap<>();

    @Override
    public synchronized NotificationPrefs update(String userId, Boolean pushEnabled, Boolean previewEnabled, Instant at) {
        NotificationPrefs current = rows.getOrDefault(userId, NotificationPrefs.DEFAULT);
        NotificationPrefs changed = new NotificationPrefs(
            pushEnabled != null ? pushEnabled : current.pushEnabled(),
            previewEnabled != null ? previewEnabled : current.previewEnabled());
        rows.put(userId, changed);
        return changed;
    }

    @Override
    public NotificationPrefs find(String userId) {
        return rows.getOrDefault(userId, NotificationPrefs.DEFAULT);
    }

    @Override
    public Map<String, NotificationPrefs> findAll(Collection<String> userIds) {
        Map<String, NotificationPrefs> found = new HashMap<>();
        for (String userId : userIds) {
            NotificationPrefs row = rows.get(userId);
            if (row != null) {
                found.put(userId, row);
            }
        }
        return found;
    }
}
