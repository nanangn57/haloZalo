package identity.http;

import identity.account.Account;
import identity.auth.TokenResponse;
import identity.service.UserSearchService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class UserController {
    private final UserSearchService users;

    public UserController(UserSearchService users) {
        this.users = users;
    }

    @GetMapping("/users")
    public TokenResponse.User find(@RequestParam(value = "q", required = false) String query) {
        Account account = users.find(query);
        return new TokenResponse.User(account.getUserId(), account.getUsername(), account.getEmail());
    }
}
