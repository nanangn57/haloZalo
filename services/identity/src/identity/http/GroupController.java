package identity.http;

import identity.group.AddMemberRequest;
import identity.group.CreateGroupRequest;
import identity.group.GroupListResponse;
import identity.group.GroupPatch;
import identity.group.GroupResponse;
import identity.group.MemberListResponse;
import identity.group.MembershipResponse;
import identity.group.TransferOwnerRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import com.fasterxml.jackson.databind.JsonNode;

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

    @GetMapping("/groups/{groupId}/members")
    public MemberListResponse listMembers(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @PathVariable String groupId
    ) {
        return groups.listMembers(callerId(userId), groupId);
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

    @PatchMapping("/groups/{groupId}")
    public GroupResponse update(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @PathVariable String groupId,
        @RequestBody JsonNode body
    ) {
        return groups.update(callerId(userId), groupId, GroupPatch.from(body));
    }

    @DeleteMapping("/groups/{groupId}")
    public ResponseEntity<Void> dissolve(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @PathVariable String groupId
    ) {
        groups.dissolve(callerId(userId), groupId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/groups/{groupId}/transfer-owner")
    public MembershipResponse transferOwner(
        @RequestAttribute(name = SessionFilter.USER_ID, required = false) String userId,
        @PathVariable String groupId,
        @RequestBody TransferOwnerRequest request
    ) {
        return groups.transferOwner(callerId(userId), groupId, request == null ? null : request.getUserId());
    }

    private static String callerId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AuthService.Rejected(AuthService.Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
        return userId;
    }
}
