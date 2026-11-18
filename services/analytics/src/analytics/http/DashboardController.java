package analytics.http;

import analytics.query.Dashboard;
import analytics.query.DashboardService;
import analytics.query.UserActivity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class DashboardController {
    private final DashboardService dashboards;

    public DashboardController(DashboardService dashboards) {
        this.dashboards = dashboards;
    }

    @GetMapping("/stats/dashboard")
    public Dashboard dashboard() {
        return dashboards.current();
    }

    @GetMapping("/me/activity")
    public UserActivity mine(@RequestAttribute(name = "userId", required = false) String userId) {
        return dashboards.mine(userId);
    }
}
