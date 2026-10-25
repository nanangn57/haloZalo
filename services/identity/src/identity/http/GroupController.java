package identity.http;

import identity.group.AddMemberRequest;
import identity.group.CreateGroupRequest;
import identity.group.GroupListResponse;
import identity.group.GroupResponse;
import identity.group.MembershipResponse;
import identity.service.AuthService;
import identity.service.GroupService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class GroupController {
    private final GroupService groups;

    public GroupController(GroupService groups) {
        this.groups = groups;
    }

    @PostMapping("/groups")
    public ResponseEntity<GroupResponse> create(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @RequestBody CreateGroupRequest request
    ) {
        return ResponseEntity.status(201).body(groups.create(callerId(userId), request));
    }

    @GetMapping("/groups")
    public GroupListResponse list(@RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId) {
        return groups.list(callerId(userId));
    }

    @PostMapping("/groups/{groupId}/members")
    public ResponseEntity<MembershipResponse> addMember(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @PathVariable String groupId,
        @RequestBody AddMemberRequest request
    ) {
        return ResponseEntity.status(201).body(groups.addMember(callerId(userId), groupId, request));
    }

    @DeleteMapping("/groups/{groupId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @PathVariable String groupId,
        @PathVariable("userId") String targetUserId
    ) {
        groups.removeMember(callerId(userId), groupId, targetUserId);
        return ResponseEntity.noContent().build();
    }

    private static String callerId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        return userId;
    }
}
