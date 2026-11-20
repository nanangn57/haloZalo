package analytics.http;

import analytics.access.PlatformAdminGate;
import analytics.query.ActivityStats;
import analytics.query.Dashboard;
import analytics.query.DashboardQuery;
import analytics.query.DashboardService;
import analytics.query.DayTotal;
import analytics.query.GroupTotal;
import analytics.query.StatsService;
import analytics.query.UserActivity;
import analytics.store.ActivityRepository;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatsAccessTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        PlatformAdminGate admins = userId -> "admin-1".equals(userId);
        StatsService stats = new StatsService(new EmptyActivities());
        DashboardService dashboards = new DashboardService(new EmptyDashboard());
        mvc = MockMvcBuilders.standaloneSetup(
                new StatsController(stats, admins),
                new DashboardController(dashboards, admins)
            )
            .setControllerAdvice(new DashboardExceptionHandler())
            .build();
    }

    @Test
    void statsForbiddenForNonAdmin() throws Exception {
        mvc.perform(get("/stats").requestAttr("userId", "user-1"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void statsOkForAdmin() throws Exception {
        mvc.perform(get("/stats").requestAttr("userId", "admin-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.logins").value(0));
    }

    @Test
    void dashboardForbiddenForNonAdmin() throws Exception {
        mvc.perform(get("/stats/dashboard").requestAttr("userId", "user-1"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void dashboardOkForAdmin() throws Exception {
        mvc.perform(get("/stats/dashboard").requestAttr("userId", "admin-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeUsers").value(0));
    }

    @Test
    void myActivityStaysOpen() throws Exception {
        mvc.perform(get("/me/activity").requestAttr("userId", "user-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.logins").value(0));
    }

    private static final class EmptyActivities implements ActivityRepository {
        @Override
        public boolean recordIfNew(String eventId, String type, Instant occurredAt) {
            return true;
        }

        @Override
        public long count(String type) {
            return 0;
        }
    }

    private static final class EmptyDashboard implements DashboardQuery {
        @Override
        public List<DayTotal> messagesByDay() {
            return List.of();
        }

        @Override
        public long activeUsers() {
            return 0;
        }

        @Override
        public List<GroupTotal> topGroups(int limit) {
            return List.of();
        }

        @Override
        public UserActivity activityOf(String userId) {
            return new UserActivity(0, 0, 0);
        }
    }
}
