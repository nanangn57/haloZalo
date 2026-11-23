package identity.http;

import identity.friend.BlockBody;
import identity.friend.FriendRequest;
import identity.friend.FriendRequestList;
import identity.friend.FriendRequestBody;
import identity.friend.FriendView;
import identity.service.AuthService;
import identity.service.FriendService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @GetMapping("/friends/requests")
    public FriendRequestList requests(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId
    ) {
        return friends.requests(callerId(userId));
    }

    @PostMapping("/friends/requests/{userId}/reject")
    public ResponseEntity<Void> reject(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @PathVariable("userId") String fromUserId
    ) {
        friends.reject(callerId(userId), fromUserId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/friends/requests/{userId}")
    public ResponseEntity<Void> cancel(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @PathVariable("userId") String toUserId
    ) {
        friends.cancel(callerId(userId), toUserId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/friends/{userId}")
    public ResponseEntity<Void> unfriend(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @PathVariable("userId") String friendUserId
    ) {
        friends.unfriend(callerId(userId), friendUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/blocks")
    public ResponseEntity<FriendView> block(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @RequestBody BlockBody body
    ) {
        return ResponseEntity.status(201).body(friends.block(callerId(userId), body == null ? null : body.getUserId()));
    }

    @DeleteMapping("/blocks/{userId}")
    public ResponseEntity<Void> unblock(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @PathVariable("userId") String blockedUserId
    ) {
        friends.unblock(callerId(userId), blockedUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/blocks")
    public Map<String, List<FriendView>> blocked(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId
    ) {
        Map<String, List<FriendView>> body = new LinkedHashMap<>();
        body.put("blocks", friends.listBlocked(callerId(userId)));
        return body;
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
