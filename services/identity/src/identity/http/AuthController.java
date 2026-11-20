package identity.http;

import identity.account.Account;
import identity.auth.LoginRequest;
import identity.auth.RegisterAccepted;
import identity.auth.RegisterRequest;
import identity.auth.TokenResponse;
import identity.account.ProfileView;
import identity.service.AuthService;
import identity.service.ProfileService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    public TokenResponse.User verify(@RequestParam(value = "token", required = false) String token) {
        Account account = auth.verify(token);
        return TokenResponse.User.from(account);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(auth.login(request));
    }

    @GetMapping("/me")
    public ProfileView me(@RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId) {
        Account account = auth.accountFor(userId);
        return profiles.view(account.getUserId());
    }
}