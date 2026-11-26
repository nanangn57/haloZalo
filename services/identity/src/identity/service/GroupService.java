package identity.service;

import identity.event.EventPublisher;
import identity.account.Account;
import identity.group.AddMemberRequest;
import identity.group.CreateGroupRequest;
import identity.group.Group;
import identity.group.GroupAction;
import identity.group.GroupListResponse;
import identity.group.GroupMemberResponse;
import identity.group.GroupPatch;
import identity.group.GroupResponse;
import identity.group.MemberListResponse;
import identity.group.Membership;
import identity.group.MembershipResponse;
import identity.group.Role;
import identity.group.RolePolicy;
import identity.group.RolePolicyRegistry;
import identity.storage.AccountRepository;
import identity.storage.DuplicateKeyException;
import identity.storage.GroupRespository;
import identity.storage.MembershipRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public final class GroupService {
    private static final int NAME_MAX = 64;
    private static final int DESCRIPTION_MAX = 280;
    private static final int AVATAR_MAX = 2048;

    private final GroupRespository groups;
    private final MembershipRepository memberships;
    private final AccountRepository accounts;
    private final RolePolicyRegistry policies;
    private final TransactionTemplate transactions;
    private final EventPublisher events;

    public GroupService(
        GroupRespository groups,
        MembershipRepository memberships,
        AccountRepository accounts,
        RolePolicyRegistry policies,
        PlatformTransactionManager transactions,
        EventPublisher events
    ) {
        this.groups = groups;
        this.memberships = memberships;
        this.accounts = accounts;
        this.policies = policies;
        this.transactions = new TransactionTemplate(transactions);
        this.events = events;
    }

    public GroupResponse create(String userId, CreateGroupRequest request) {
        // Tên tuỳ chọn: rỗng → client hiện tên theo thành viên (Zalo-style).
        String name = normalizeOptionalName(request == null ? null : request.getName());
        Instant now = Instant.now();
        String ownerId = canonical(userId);
        List<String> initialMembers = resolveInitialMembers(
            ownerId,
            request == null ? List.of() : request.getMemberIds()
        );
        Group group = new Group(UUID.randomUUID().toString(), name, null, now, ownerId);
        Membership owner = new Membership(group.getGroupId(), ownerId, now, Role.OWNER);
        transactions.executeWithoutResult(status -> {
            groups.save(group);
            memberships.save(owner);
            for (String memberId : initialMembers) {
                memberships.save(new Membership(group.getGroupId(), memberId, now, Role.MEMBER));
            }
        });
        events.publishGroupCreated(group.getGroupId(), ownerId, 1 + initialMembers.size());
        for (String memberId : initialMembers) {
            events.publishMemberAdded(group.getGroupId(), ownerId, memberId);
        }
        return response(group, Role.OWNER);
    }

    /** Deduplicate, drop owner/self, reject unknown or invalid ids. */
    private List<String> resolveInitialMembers(String ownerId, List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        List<String> resolved = new ArrayList<>();
        for (String raw : rawIds) {
            String memberId = canonical(raw);
            if (!validUuid(memberId)) {
                throw new Rejected(Rejected.Reason.VALIDATION, "Invalid member request");
            }
            if (memberId.equals(ownerId) || resolved.contains(memberId)) {
                continue;
            }
            if (accounts.findById(memberId) == null) {
                throw new Rejected(Rejected.Reason.NOT_FOUND, "User not found");
            }
            resolved.add(memberId);
        }
        return List.copyOf(resolved);
    }

    public GroupListResponse list(String userId) {
        List<GroupResponse> responses = new ArrayList<>();
        for (Membership membership : memberships.listByUser(canonical(userId))) {
            Group group = groups.findById(membership.getGroupId());
            if (group != null) {
                responses.add(response(group, membership.getRole()));
            }
        }
        return new GroupListResponse(List.copyOf(responses));
    }

    public MemberListResponse listMembers(String callerId, String groupId) {
        String group = canonical(groupId);
        String caller = canonical(callerId);
        if (!validUuid(group)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid group id");
        }
        requireGroup(group);
        if (memberships.find(group, caller) == null) {
            throw new Rejected(Rejected.Reason.FORBIDDEN, "Not allowed to change this group");
        }
        List<Membership> rows = new ArrayList<>(memberships.listByGroup(group));
        rows.sort(Comparator.comparing(Membership::getJoinedAt).thenComparing(Membership::getUserId));
        List<GroupMemberResponse> responses = new ArrayList<>();
        for (Membership membership : rows) {
            Account account = accounts.findById(membership.getUserId());
            String username = account == null ? null : account.getUsername();
            responses.add(new GroupMemberResponse(membership.getUserId(), username, membership.getRole()));
        }
        return new MemberListResponse(List.copyOf(responses));
    }

    public MembershipResponse addMember(String callerId, String groupId, AddMemberRequest request) {
        String memberId = canonical(request == null ? null : request.getUserId());
        String group = canonical(groupId);
        String caller = canonical(callerId);
        if (!validUuid(group) || !validUuid(memberId)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid member request");
        }
        requireGroup(group);
        requireAllowed(caller, group, GroupAction.ADD_MEMBER);
        if (accounts.findById(memberId) == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "User not found");
        }
        if (memberships.find(group, memberId) != null) {
            throw new Rejected(Rejected.Reason.CONFLICT, "User is already a member");
        }
        try {
            memberships.save(new Membership(group, memberId, Instant.now(), Role.MEMBER));
        } catch (DuplicateKeyException ex) {
            throw new Rejected(Rejected.Reason.CONFLICT, "User is already a member");
        }
        events.publishMemberAdded(group, caller, memberId);
        return new MembershipResponse(group, memberId, Role.MEMBER);
    }

    public void removeMember(String callerId, String groupId, String targetUserId) {
        String group = canonical(groupId);
        String target = canonical(targetUserId);
        String caller = canonical(callerId);
        if (!validUuid(group) || !validUuid(target)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid member request");
        }
        requireGroup(group);
        Membership self = memberships.find(group, caller);
        if (target.equals(caller) && self != null && self.getRole() == Role.OWNER) {
            leaveAsOwner(group, caller);
            return;
        }
        GroupAction action = target.equals(caller) ? GroupAction.LEAVE : GroupAction.REMOVE_MEMBER;
        requireAllowed(caller, group, action);
        Membership targetMembership = memberships.find(group, target);
        if (targetMembership == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "User not found");
        }
        if (targetMembership.getRole() == Role.OWNER) {
            throw new Rejected(Rejected.Reason.FORBIDDEN, "Not allowed to change this group");
        }
        memberships.delete(group, target);
        if (target.equals(caller)) {
            events.publishMemberLeft(group, target);
        } else {
            events.publishMemberRemoved(group, caller, target);
        }
    }

    /** An owner who is alone closes the group by leaving. With other members the owner must hand over first. */
    private void leaveAsOwner(String groupId, String ownerId) {
        if (memberships.listByGroup(groupId).size() > 1) {
            throw new Rejected(Rejected.Reason.FORBIDDEN, "Transfer ownership before leaving");
        }
        dissolveGroup(groupId);
        events.publishGroupDissolved(groupId, ownerId);
    }

    public MembershipResponse transferOwner(String callerId, String groupId, String newOwnerId) {
        String group = canonical(groupId);
        String caller = canonical(callerId);
        String target = canonical(newOwnerId);
        if (!validUuid(group) || !validUuid(target)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid transfer request");
        }
        Group current = requireGroup(group);
        requireAllowed(caller, group, GroupAction.TRANSFER_OWNER);
        if (target.equals(caller)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Already the owner");
        }
        if (memberships.find(group, target) == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "User not found");
        }
        transactions.executeWithoutResult(status -> {
            memberships.updateRole(group, target, Role.OWNER);
            memberships.updateRole(group, caller, Role.MEMBER);
            groups.update(current.withOwnerId(target));
        });
        events.publishOwnerTransferred(group, caller, target);
        return new MembershipResponse(group, target, Role.OWNER);
    }

    public void dissolve(String callerId, String groupId) {
        String group = canonical(groupId);
        String caller = canonical(callerId);
        if (!validUuid(group)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid group id");
        }
        requireGroup(group);
        requireAllowed(caller, group, GroupAction.DISSOLVE);
        dissolveGroup(group);
        events.publishGroupDissolved(group, caller);
    }

    public GroupResponse update(String callerId, String groupId, GroupPatch patch) {
        String group = canonical(groupId);
        String caller = canonical(callerId);
        if (!validUuid(group)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid group id");
        }
        Group current = requireGroup(group);
        requireAllowed(caller, group, GroupAction.UPDATE_GROUP);
        if (patch == null || (!patch.hasName() && !patch.hasDescription() && !patch.hasAvatar())) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Nothing to update");
        }
        String name = current.getGroupName();
        String description = current.getGroupDescription();
        String avatar = current.getGroupAvatar();
        if (patch.hasName()) {
            // Chuỗi rỗng xoá tên đặt tay → client hiện tên thành viên.
            name = normalizeOptionalName(patch.getName());
        }
        if (patch.hasDescription()) {
            description = optionalText(patch.getDescription(), DESCRIPTION_MAX, "Invalid group description");
        }
        if (patch.hasAvatar()) {
            avatar = optionalText(patch.getAvatar(), AVATAR_MAX, "Invalid group avatar");
        }
        Group updated = current.withDetails(name, description, avatar);
        groups.update(updated);
        return response(updated, Role.OWNER);
    }

    private void dissolveGroup(String groupId) {
        transactions.executeWithoutResult(status -> {
            memberships.deleteByGroup(groupId);
            groups.delete(groupId);
        });
    }

    private static String optionalText(String value, int max, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > max) {
            throw new Rejected(Rejected.Reason.VALIDATION, message);
        }
        return value;
    }

    /** Null/blank → ""; quá dài → reject. */
    private static String normalizeOptionalName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > NAME_MAX) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid group name");
        }
        return trimmed;
    }

    private Group requireGroup(String groupId) {
        Group group = groups.findById(groupId);
        if (group == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "Group not found");
        }
        return group;
    }

    private void requireAllowed(String callerId, String groupId, GroupAction action) {
        Membership caller = memberships.find(groupId, callerId);
        if (caller == null) {
            throw new Rejected(Rejected.Reason.FORBIDDEN, "Not allowed to change this group");
        }
        RolePolicy policy = policies.getPolicy(caller.getRole());
        if (!policy.alows(action)) {
            throw new Rejected(Rejected.Reason.FORBIDDEN, "Not allowed to change this group");
        }
    }

    private static GroupResponse response(Group group, Role role) {
        return new GroupResponse(
            group.getGroupId(),
            group.getGroupName(),
            group.getGroupOwnerId(),
            role,
            group.getGroupDescription(),
            group.getGroupAvatar()
        );
    }

    private static String canonical(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }

    private static boolean validUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static final class Rejected extends RuntimeException {
        public enum Reason {
            VALIDATION,
            FORBIDDEN,
            NOT_FOUND,
            CONFLICT
        }

        private final Reason reason;

        public Rejected(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }
}
