package push;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public final class PostgresNotificationPrefsRepository implements NotificationPrefsRepository {
    private final JdbcTemplate jdbc;

    public PostgresNotificationPrefsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public NotificationPrefs update(String userId, Boolean pushEnabled, Boolean previewEnabled, Instant at) {
        // A missing row starts from true; an existing row keeps every field the patch leaves null.
        return jdbc.query(
            "INSERT INTO notification_prefs (user_id, push_enabled, preview_enabled, updated_at) "
                + "VALUES (?, COALESCE(CAST(? AS boolean), TRUE), COALESCE(CAST(? AS boolean), TRUE), ?) "
                + "ON CONFLICT (user_id) DO UPDATE SET "
                + "push_enabled = COALESCE(CAST(? AS boolean), notification_prefs.push_enabled), "
                + "preview_enabled = COALESCE(CAST(? AS boolean), notification_prefs.preview_enabled), "
                + "updated_at = EXCLUDED.updated_at "
                + "RETURNING push_enabled, preview_enabled",
            rows -> {
                rows.next();
                return new NotificationPrefs(rows.getBoolean(1), rows.getBoolean(2));
            },
            UUID.fromString(userId), pushEnabled, previewEnabled, Timestamp.from(at), pushEnabled, previewEnabled);
    }

    @Override
    public NotificationPrefs find(String userId) {
        return findAll(List.of(userId)).getOrDefault(userId, NotificationPrefs.DEFAULT);
    }

    @Override
    public Map<String, NotificationPrefs> findAll(Collection<String> userIds) {
        Map<String, NotificationPrefs> found = new HashMap<>();
        if (userIds.isEmpty()) {
            return found;
        }
        String placeholders = String.join(", ", Collections.nCopies(userIds.size(), "?"));
        jdbc.query(
            "SELECT user_id, push_enabled, preview_enabled FROM notification_prefs WHERE user_id IN (" + placeholders + ")",
            rows -> {
                found.put(rows.getString(1), new NotificationPrefs(rows.getBoolean(2), rows.getBoolean(3)));
            },
            userIds.stream().map(UUID::fromString).toArray());
        return found;
    }
}
