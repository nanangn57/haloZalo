package http;

import account.Account;
import auth.LoginRequest;
import auth.RegisterAccepted;
import auth.RegisterRequest;
import auth.TokenResponse;
import service.AuthService;

import java.util.LinkedHashMap;
import java.util.Map;

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

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<RegisterAccepted> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.accepted().body(auth.register(request));
    }

    @GetMapping("/auth/verify")
    public TokenResponse.User verify(@RequestParam(value = "token", required = false) String token) {
        Account account = auth.verify(token);
        return new TokenResponse.User(account.getUserId(), account.getUsername(), account.getEmail());
    }

    @PostMapping("/auth/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(auth.login(request));
    }

    @GetMapping("/me")
    public Map<String, String> me(@RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId) {
        Account account = auth.accountFor(userId);
        Map<String, String> user = new LinkedHashMap<>();
        user.put("userId", account.getUserId());
        user.put("username", account.getUsername());
        user.put("email", account.getEmail());
        return user;
    }
}