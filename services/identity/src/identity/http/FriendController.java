package identity.http;

import identity.friend.FriendRequest;
import identity.friend.FriendRequestBody;
import identity.friend.FriendView;
import identity.service.AuthService;
import identity.service.FriendService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class FriendController {
    private final FriendService friends;

    public FriendController(FriendService friends) {
        this.friends = friends;
    }

    @PostMapping("/friends/requests")
    public ResponseEntity<FriendRequest> send(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @RequestBody FriendRequestBody body
    ) {
        FriendRequest request = friends.send(callerId(userId), body == null ? null : body.getUserId());
        return ResponseEntity.status(201).body(request);
    }

    @PostMapping("/friends/requests/{userId}/accept")
    public FriendView accept(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @PathVariable("userId") String fromUserId
    ) {
        return friends.accept(callerId(userId), fromUserId);
    }

    @GetMapping("/friends")
    public Map<String, List<FriendView>> list(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId
    ) {
        Map<String, List<FriendView>> body = new LinkedHashMap<>();
        body.put("friends", friends.list(callerId(userId)));
        return body;
    }

    private static String callerId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        return userId;
    }
}
