package identity.http;

import identity.account.Account;
import identity.account.ProfileView;
import identity.auth.LoginRequest;
import identity.auth.RegisterAccepted;
import identity.auth.RegisterRequest;
import identity.auth.ResendVerificationRequest;
import identity.auth.SessionInfo;
import identity.auth.SessionList;
import identity.auth.TokenResponse;
import identity.service.AuthService;
import identity.service.ProfileService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class AuthController {
    private final AuthService auth;
    private final ProfileService profiles;

    public AuthController(AuthService auth, ProfileService profiles) {
        this.auth = auth;
        this.profiles = profiles;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<RegisterAccepted> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.accepted().body(auth.register(request));
    }

    @GetMapping("/auth/verify")
    public TokenResponse verify(
        @RequestParam(value = "token", required = false) String token,
        @RequestParam(value = "deviceId", required = false) String deviceId,
        @RequestParam(value = "deviceLabel", required = false) String deviceLabel
    ) {
        return auth.verify(token, deviceId, deviceLabel);
    }

    @PostMapping("/auth/verify/resend")
    public ResponseEntity<RegisterAccepted> resend(@RequestBody ResendVerificationRequest request) {
        return ResponseEntity.accepted().body(auth.resendVerification(request == null ? null : request.getEmail()));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(auth.login(request));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(
        @RequestAttribute(name = SessionFilter.SESSION_ID, required = false) String sessionId
    ) {
        auth.logout(sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/auth/sessions")
    public SessionList sessions(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId
    ) {
        Account account = auth.accountFor(userId);
        return new SessionList(auth.listSessions(account.getUserId()).stream().map(SessionInfo::from).toList());
    }

    @DeleteMapping("/auth/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @PathVariable("sessionId") String sessionId
    ) {
        Account account = auth.accountFor(userId);
        auth.revokeSession(account.getUserId(), sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ProfileView me(@RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId) {
        Account account = auth.accountFor(userId);
        return profiles.view(account.getUserId());
    }
}
