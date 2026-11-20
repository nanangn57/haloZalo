package analytics.http;

import analytics.access.PlatformAdminGate;
import analytics.query.ActivityStats;
import analytics.query.StatsService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class StatsController {
    private final StatsService stats;
    private final PlatformAdminGate admins;

    public StatsController(StatsService stats, PlatformAdminGate admins) {
        this.stats = stats;
        this.admins = admins;
    }

    @GetMapping("/stats")
    public ActivityStats current(
        @RequestAttribute(name = "userId", required = false) String userId
    ) {
        requireAdmin(userId);
        return stats.current();
    }

    private void requireAdmin(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ForbiddenStats("Missing or invalid access token");
        }
        if (!admins.isAdmin(userId)) {
            throw new ForbiddenStats("Platform admin role required");
        }
    }

    public static final class ForbiddenStats extends RuntimeException {
        public ForbiddenStats(String message) {
            super(message);
        }
    }
}
