package identity.http;

import analytics.query.DashboardService;
import identity.service.AdminService;
import identity.service.AuthService;
import identity.service.ProfileService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class AdminController {
    private final AdminService admin;
    private final ProfileService profiles;
    private final DashboardService dashboards;

    public AdminController(AdminService admin, ProfileService profiles, DashboardService dashboards) {
        this.admin = admin;
        this.profiles = profiles;
        this.dashboards = dashboards;
    }

    @GetMapping("/admin/users/{userId}")
    public AdminUserSummary summary(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String callerId,
        @PathVariable("userId") String userId
    ) {
        admin.requireAdmin(requireCaller(callerId));
        try {
            return new AdminUserSummary(profiles.view(userId), dashboards.mine(userId));
        } catch (ProfileService.Rejected rejected) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.NOT_FOUND, rejected.getMessage());
        }
    }

    @PostMapping("/admin/users/{userId}/ban")
    public ResponseEntity<Void> ban(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String callerId,
        @PathVariable("userId") String userId
    ) {
        admin.ban(requireCaller(callerId), userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/users/{userId}/unban")
    public ResponseEntity<Void> unban(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String callerId,
        @PathVariable("userId") String userId
    ) {
        admin.unban(requireCaller(callerId), userId);
        return ResponseEntity.noContent().build();
    }

    private static String requireCaller(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        return userId;
    }
}
