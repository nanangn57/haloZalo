package service;

import group.AddMemberRequest;
import group.CreateGroupRequest;
import group.Group;
import group.GroupAction;
import group.GroupListResponse;
import group.GroupResponse;
import group.Membership;
import group.MembershipResponse;
import group.Role;
import group.RolePolicy;
import group.RolePolicyRegistry;
import storage.AccountRepository;
import storage.DuplicateKeyException;
import storage.GroupRespository;
import storage.MembershipRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public final class GroupService {
    private static final int NAME_MAX = 64;

    private final GroupRespository groups;
    private final MembershipRepository memberships;
    private final AccountRepository accounts;
    private final RolePolicyRegistry policies;
    private final TransactionTemplate transactions;

    public GroupService(
        GroupRespository groups,
        MembershipRepository memberships,
        AccountRepository accounts,
        RolePolicyRegistry policies,
        PlatformTransactionManager transactions
    ) {
        this.groups = groups;
        this.memberships = memberships;
        this.accounts = accounts;
        this.policies = policies;
        this.transactions = new TransactionTemplate(transactions);
    }

    public GroupResponse create(String userId, CreateGroupRequest request) {
        String name = request == null ? null : request.getName();
        if (name == null || name.isBlank() || name.length() > NAME_MAX) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid group name");
        }
        Instant now = Instant.now();
        String ownerId = canonical(userId);
        Group group = new Group(UUID.randomUUID().toString(), name, null, now, ownerId);
        Membership owner = new Membership(group.getGroupId(), ownerId, now, Role.OWNER);
        transactions.executeWithoutResult(status -> {
            groups.save(group);
            memberships.save(owner);
        });
        return response(group, Role.OWNER);
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
        GroupAction action = target.equals(caller) ? GroupAction.LEAVE : GroupAction.REMOVE_MEMBER;
        requireAllowed(caller, group, action);
        if (memberships.find(group, target) == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "User not found");
        }
        memberships.delete(group, target);
    }

    private void requireGroup(String groupId) {
        if (groups.findById(groupId) == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "Group not found");
        }
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
        return new GroupResponse(group.getGroupId(), group.getGroupName(), group.getGroupOwnerId(), role);
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
