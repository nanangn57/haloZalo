package member;

import service.Ids;
import service.MessageService;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TEMPORARY, see {@link StandaloneGroupMembers}: sets who is in a group while identity is not in the same process.
 * PUT /internal/groups/{groupId}/members with { "memberIds": [...] } replaces the members.
 */
@RestController
public final class StandaloneGroupController {
    private final StandaloneGroupMembers groups;

    public StandaloneGroupController(StandaloneGroupMembers groups) {
        this.groups = groups;
    }

    @PutMapping("/internal/groups/{groupId}/members")
    public ResponseEntity<Void> replace(@PathVariable String groupId, @RequestBody Members request) {
        String group = Ids.canonical(groupId);
        if (group == null || request.memberIds() == null) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION, "groupId and memberIds are required");
        }
        Set<String> members = new HashSet<>();
        for (String member : request.memberIds()) {
            String id = Ids.canonical(member);
            if (id == null) {
                throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION, "memberId must be a UUID");
            }
            members.add(id);
        }
        groups.replace(group, members);
        return ResponseEntity.noContent().build();
    }

    public record Members(List<String> memberIds) {
        @JsonCreator
        public Members(@JsonProperty("memberIds") List<String> memberIds) {
            this.memberIds = memberIds;
        }
    }
}
