package push;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

public interface NotificationPrefsRepository {
    /**
     * Applies the non-null fields to the user's prefs, creating them when absent, and returns the prefs after the change.
     */
    NotificationPrefs update(String userId, Boolean pushEnabled, Boolean previewEnabled, Instant at);

    /**
     * The user's prefs, or {@link NotificationPrefs#DEFAULT} when the user never changed them.
     */
    NotificationPrefs find(String userId);

    /**
     * Prefs of each given user. Users without a stored row are absent: they have the defaults.
     */
    Map<String, NotificationPrefs> findAll(Collection<String> userIds);
}
