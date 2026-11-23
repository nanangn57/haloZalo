package push;

import http.UserId;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class NotificationPrefsController {
    private final NotificationPrefsService prefs;

    public NotificationPrefsController(NotificationPrefsService prefs) {
        this.prefs = prefs;
    }

    @GetMapping("/me/notifications")
    public PrefsBody get(@UserId String userId) {
        return PrefsBody.of(prefs.get(userId));
    }

    @PatchMapping("/me/notifications")
    public PrefsBody update(@UserId String userId, @RequestBody PrefsPatchBody body) {
        return PrefsBody.of(prefs.update(userId, body.pushEnabled(), body.previewEnabled()));
    }

    public record PrefsBody(
        @JsonProperty("pushEnabled") boolean pushEnabled,
        @JsonProperty("previewEnabled") boolean previewEnabled
    ) {
        static PrefsBody of(NotificationPrefs prefs) {
            return new PrefsBody(prefs.pushEnabled(), prefs.previewEnabled());
        }
    }

    public record PrefsPatchBody(
        @JsonProperty("pushEnabled") Boolean pushEnabled,
        @JsonProperty("previewEnabled") Boolean previewEnabled
    ) {
    }
}
