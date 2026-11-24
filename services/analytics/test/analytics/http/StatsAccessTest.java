package analytics.http;

import analytics.access.PlatformAdminGate;
import analytics.query.ConversationTotal;
import analytics.query.DashboardQuery;
import analytics.query.DashboardService;
import analytics.query.DateRange;
import analytics.query.DayTotal;
import analytics.query.DirectGroupSplit;
import analytics.query.FriendActivity;
import analytics.query.GroupTotal;
import analytics.query.HeatmapCell;
import analytics.query.MediaDayTotal;
import analytics.query.MediaTextSplit;
import analytics.query.MessageDayTotal;
import analytics.query.StatsService;
import analytics.query.UserActivity;
import analytics.store.ActivityRepository;

import java.time.Instant;
import java.time.LocalDate;
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
            .andExpect(jsonPath("$.activeUsers").value(0))
            .andExpect(jsonPath("$.dau").value(0))
            .andExpect(jsonPath("$.verifyFunnel.registered").value(0));
    }

    @Test
    void myActivityStaysOpen() throws Exception {
        mvc.perform(get("/me/activity").requestAttr("userId", "user-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.logins").value(0))
            .andExpect(jsonPath("$.messagesReceived").value(0))
            .andExpect(jsonPath("$.friends.count").value(0));
    }

    @Test
    void dashboardFiltersDaysAndEchoesRange() throws Exception {
        mvc.perform(get("/stats/dashboard").requestAttr("userId", "admin-1")
                .param("from", "2026-10-02").param("to", "2026-10-03"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messagesByDay.length()").value(2))
            .andExpect(jsonPath("$.messagesByDay[0].day").value("2026-10-02"))
            .andExpect(jsonPath("$.from").value("2026-10-02"))
            .andExpect(jsonPath("$.to").value("2026-10-03"));
    }

    @Test
    void dashboardWithoutRangeHasNoEchoAndAllDays() throws Exception {
        mvc.perform(get("/stats/dashboard").requestAttr("userId", "admin-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messagesByDay.length()").value(4))
            .andExpect(jsonPath("$.from").doesNotExist());
    }

    @Test
    void dashboardRangeStillNeedsAdmin() throws Exception {
        mvc.perform(get("/stats/dashboard").requestAttr("userId", "user-1").param("from", "2026-10-02"))
            .andExpect(status().isForbidden());
    }

    @Test
    void badRangeIsValidationError() throws Exception {
        mvc.perform(get("/stats/dashboard").requestAttr("userId", "admin-1").param("from", "nope"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/me/activity").requestAttr("userId", "user-1")
                .param("from", "2026-10-09").param("to", "2026-10-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void myActivityEchoesRangeWithFilteredCounters() throws Exception {
        mvc.perform(get("/me/activity").requestAttr("userId", "user-1")
                .param("from", "2026-10-01").param("to", "2026-10-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messagesSent").value(7))
            .andExpect(jsonPath("$.from").value("2026-10-01"))
            .andExpect(jsonPath("$.to").value("2026-10-31"));
    }

    @Test
    void myActivityWithoutRangeOmitsFromAndTo() throws Exception {
        mvc.perform(get("/me/activity").requestAttr("userId", "user-1"))
            .andExpect(jsonPath("$.from").doesNotExist())
            .andExpect(jsonPath("$.to").doesNotExist());
    }

    @Test
    void myActivityWithoutSessionIs401() throws Exception {
        mvc.perform(get("/me/activity").param("from", "2026-10-01"))
            .andExpect(status().isUnauthorized());
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
            return List.of(
                new DayTotal("2026-10-01", 1),
                new DayTotal("2026-10-02", 2),
                new DayTotal("2026-10-03", 3),
                new DayTotal("2026-10-04", 4));
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
        public UserActivity activityOf(String userId, DateRange range) {
            long sent = "user-1".equals(userId) ? 7 : 0;
            return new UserActivity(
                0,
                sent,
                0,
                0,
                0,
                List.<MessageDayTotal>of(),
                new MediaTextSplit(0, 0),
                new DirectGroupSplit(0, 0),
                List.<HeatmapCell>of(),
                new FriendActivity(0, 0, 0, 0),
                List.<ConversationTotal>of(),
                range
            );
        }

        @Override
        public long distinctActive(LocalDate from, LocalDate to) {
            return 0;
        }

        @Override
        public List<DayTotal> registrationsByDay(DateRange range) {
            return List.of();
        }

        @Override
        public long sumDayTotal(List<DayTotal> days) {
            return days.stream().mapToLong(DayTotal::getTotal).sum();
        }

        @Override
        public long verifications(DateRange range) {
            return 0;
        }

        @Override
        public long verifiedInRegistrationCohort(DateRange range) {
            return 0;
        }

        @Override
        public long firstMessageInRegistrationCohort(DateRange range) {
            return 0;
        }

        @Override
        public long messengersInRange(DateRange range) {
            return 0;
        }

        @Override
        public long activeInRange(DateRange range) {
            return 0;
        }

        @Override
        public long friendRequests(DateRange range) {
            return 0;
        }

        @Override
        public long friendAccepted(DateRange range) {
            return 0;
        }

        @Override
        public long groupsCreated(DateRange range) {
            return 0;
        }

        @Override
        public double avgMembersPerGroup(DateRange range) {
            return 0;
        }

        @Override
        public List<MediaDayTotal> mediaByDay(DateRange range) {
            return List.of();
        }

        @Override
        public List<HeatmapCell> platformHeatmap() {
            return List.of();
        }
    }
}
