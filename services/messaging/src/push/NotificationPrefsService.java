package push;

import service.Ids;
import service.MessageService;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public final class NotificationPrefsService {
    private final NotificationPrefsRepository prefs;

    public NotificationPrefsService(NotificationPrefsRepository prefs) {
        this.prefs = prefs;
    }

    public NotificationPrefs get(String userId) {
        return prefs.find(user(userId));
    }

    /**
     * A null field stays as it is. Sending neither field is a mistake, not a no-op.
     */
    public NotificationPrefs update(String userId, Boolean pushEnabled, Boolean previewEnabled) {
        String user = user(userId);
        if (pushEnabled == null && previewEnabled == null) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION,
                "pushEnabled or previewEnabled is required");
        }
        return prefs.update(user, pushEnabled, previewEnabled, Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    private static String user(String userId) {
        String id = Ids.canonical(userId);
        if (id == null) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.UNAUTHENTICATED,
                "Missing or invalid access token");
        }
        return id;
    }
}
