package identity.http;

import identity.auth.PasswordChangeRequest;
import identity.auth.PasswordForgotRequest;
import identity.auth.PasswordResetRequest;
import identity.service.AuthService;
import identity.service.PasswordService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class PasswordController {
    private final PasswordService passwords;

    public PasswordController(PasswordService passwords) {
        this.passwords = passwords;
    }

    @PostMapping("/auth/password/forgot")
    public ResponseEntity<Void> forgot(@RequestBody PasswordForgotRequest request) {
        passwords.forgot(request == null ? null : request.getEmail());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/auth/password/reset")
    public ResponseEntity<Void> reset(@RequestBody PasswordResetRequest request) {
        passwords.reset(
            request == null ? null : request.getToken(),
            request == null ? null : request.getPassword()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/password/change")
    public ResponseEntity<Void> change(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @RequestAttribute(name = SessionFilter.SESSION_ID, required = false) String sessionId,
        @RequestBody PasswordChangeRequest request
    ) {
        if (userId == null || userId.isBlank()) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        passwords.change(
            userId,
            sessionId,
            request == null ? null : request.getCurrentPassword(),
            request == null ? null : request.getNewPassword(),
            request != null && Boolean.TRUE.equals(request.getRevokeOtherSessions())
        );
        return ResponseEntity.noContent().build();
    }
}
