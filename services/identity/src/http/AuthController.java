package http;

import account.Account;
import auth.LoginRequest;
import auth.RegisterRequest;
import auth.TokenResponse;
import service.AuthService;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class AuthController {
    private static final String BEARER = "Bearer ";

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<TokenResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).body(auth.register(request));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(auth.login(request));
    }

    @GetMapping("/me")
    public Map<String, String> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Account account = auth.currentAccount(sessionId(authorization));
        Map<String, String> user = new LinkedHashMap<>();
        user.put("userId", account.getUserId());
        user.put("username", account.getUsername());
        user.put("email", account.getEmail());
        return user;
    }

    private static String sessionId(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        String sessionId = authorization.substring(BEARER.length()).trim();
        if (sessionId.isEmpty()) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        return sessionId;
    }
}