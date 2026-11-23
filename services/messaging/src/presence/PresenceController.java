package presence;

import http.UserId;
import service.Ids;
import service.MessageService;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
public final class PresenceController {
    public static final int IDS_MAX = 100;

    private final PresenceLookup presences;

    public PresenceController(PresenceLookup presences) {
        this.presences = presences;
    }

    @GetMapping("/presence")
    public PresenceListBody presence(@UserId String userId, @RequestParam(required = false) String ids) {
        if (Ids.canonical(userId) == null) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.UNAUTHENTICATED,
                "Missing or invalid access token");
        }
        return new PresenceListBody(presences.of(parse(ids)).stream().map(PresenceBody::of).toList());
    }

    private static Set<String> parse(String ids) {
        if (ids == null || ids.isBlank()) {
            throw invalid("ids is required");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String part : ids.split(",")) {
            String raw = part.trim();
            if (raw.isEmpty()) {
                continue;
            }
            String id = Ids.canonical(raw);
            if (id == null) {
                throw invalid("ids must be UUIDs");
            }
            unique.add(id);
        }
        if (unique.isEmpty() || unique.size() > IDS_MAX) {
            throw invalid("ids must hold 1 to " + IDS_MAX + " user ids");
        }
        return unique;
    }

    private static MessageService.Rejected invalid(String message) {
        return new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION, message);
    }

    public record PresenceListBody(List<PresenceBody> presences) {
    }

    public record PresenceBody(
        String userId,
        boolean online,
        @JsonInclude(JsonInclude.Include.NON_NULL) String lastSeenAt
    ) {
        static PresenceBody of(Presence presence) {
            return new PresenceBody(presence.userId(), presence.online(),
                presence.lastSeenAt() == null ? null : presence.lastSeenAt().toString());
        }
    }
}
