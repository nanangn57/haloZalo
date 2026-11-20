package analytics.http;

import analytics.access.PlatformAdminGate;
import analytics.query.Dashboard;
import analytics.query.DashboardService;
import analytics.query.UserActivity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class DashboardController {
    private final DashboardService dashboards;
    private final PlatformAdminGate admins;

    public DashboardController(DashboardService dashboards, PlatformAdminGate admins) {
        this.dashboards = dashboards;
        this.admins = admins;
    }

    @GetMapping("/stats/dashboard")
    public Dashboard dashboard(
        @RequestAttribute(name = "userId", required = false) String userId
    ) {
        requireAdmin(userId);
        return dashboards.current();
    }

    @GetMapping("/me/activity")
    public UserActivity mine(@RequestAttribute(name = "userId", required = false) String userId) {
        return dashboards.mine(userId);
    }

    private void requireAdmin(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new StatsController.ForbiddenStats("Missing or invalid access token");
        }
        if (!admins.isAdmin(userId)) {
            throw new StatsController.ForbiddenStats("Platform admin role required");
        }
    }
}
