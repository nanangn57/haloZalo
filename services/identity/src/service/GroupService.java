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
        Group group = new Group(UUID.randomUUID().toString(), name, null, now, userId);
        Membership owner = new Membership(group.getGroupId(), userId, now, Role.OWNER);
        transactions.executeWithoutResult(status -> {
            groups.save(group);
            memberships.save(owner);
        });
        return response(group, Role.OWNER);
    }

    public GroupListResponse list(String userId) {
        List<GroupResponse> responses = new ArrayList<>();
        for (Membership membership : memberships.listByUser(userId)) {
            Group group = groups.findById(membership.getGroupId());
            if (group != null) {
                responses.add(response(group, membership.getRole()));
            }
        }
        return new GroupListResponse(List.copyOf(responses));
    }

    public MembershipResponse addMember(String callerId, String groupId, AddMemberRequest request) {
        String memberId = request == null ? null : request.getUserId();
        if (!validUuid(groupId) || !validUuid(memberId)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid member request");
        }
        requireGroup(groupId);
        requireAllowed(callerId, groupId, GroupAction.ADD_MEMBER);
        if (accounts.findById(memberId) == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "User not found");
        }
        if (memberships.find(groupId, memberId) != null) {
            throw new Rejected(Rejected.Reason.CONFLICT, "User is already a member");
        }
        memberships.save(new Membership(groupId, memberId, Instant.now(), Role.MEMBER));
        return new MembershipResponse(groupId, memberId, Role.MEMBER);
    }

    public void removeMember(String callerId, String groupId, String targetUserId) {
        if (!validUuid(groupId) || !validUuid(targetUserId)) {
            throw new Rejected(Rejected.Reason.VALIDATION, "Invalid member request");
        }
        requireGroup(groupId);
        GroupAction action = callerId.equals(targetUserId) ? GroupAction.LEAVE : GroupAction.REMOVE_MEMBER;
        requireAllowed(callerId, groupId, action);
        if (memberships.find(groupId, targetUserId) == null) {
            throw new Rejected(Rejected.Reason.NOT_FOUND, "User not found");
        }
        memberships.delete(groupId, targetUserId);
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
