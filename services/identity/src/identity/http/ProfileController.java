package identity.http;

import identity.account.ProfilePatch;
import identity.account.ProfileView;
import identity.service.AuthService;
import identity.service.ProfileService;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @PatchMapping("/me")
    public ProfileView update(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @RequestBody JsonNode body
    ) {
        return profiles.update(callerId(userId), ProfilePatch.from(body));
    }

    private static String callerId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        return userId;
    }
}
