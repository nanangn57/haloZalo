package service;

import account.Account;
import group.AddMemberRequest;
import group.CreateGroupRequest;
import group.Group;
import group.GroupResponse;
import group.Membership;
import group.MembershipResponse;
import group.OwnerMemberPolicyRegistry;
import group.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import storage.AccountRepository;
import storage.GroupRespository;
import storage.MembershipRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroupServiceTest {
    private MemoryGroups groups;
    private MemoryMemberships memberships;
    private MemoryAccounts accounts;
    private GroupService service;
    private String ownerId;
    private String memberId;

    @BeforeEach
    void setUp() {
        groups = new MemoryGroups();
        memberships = new MemoryMemberships();
        accounts = new MemoryAccounts();
        service = new GroupService(groups, memberships, accounts, new OwnerMemberPolicyRegistry(), new ImmediateTransactions());
        ownerId = saveAccount("owner");
        memberId = saveAccount("member");
    }

    @Test
    void createStoresOwnerMembership() {
        GroupResponse created = service.create(ownerId, new CreateGroupRequest("nhom"));

        assertEquals("nhom", created.getName());
        assertEquals(ownerId, created.getOwnerId());
        assertEquals(Role.OWNER, created.getRole());
        assertEquals(Role.OWNER, memberships.find(created.getGroupId(), ownerId).getRole());
    }

    @Test
    void createRejectsBlankName() {
        GroupService.Rejected rejected = assertThrows(GroupService.Rejected.class,
            () -> service.create(ownerId, new CreateGroupRequest("  ")));

        assertEquals(GroupService.Rejected.Reason.VALIDATION, rejected.getReason());
        assertEquals(0, groups.size());
    }

    @Test
    void ownerAddsAndRemovesMember() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();

        MembershipResponse added = service.addMember(ownerId, groupId, new AddMemberRequest(memberId));
        service.removeMember(ownerId, groupId, memberId);

        assertEquals(Role.MEMBER, added.getRole());
        assertNull(memberships.find(groupId, memberId));
        assertEquals(Role.OWNER, memberships.find(groupId, ownerId).getRole());
    }

    @Test
    void memberLeaves() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();
        service.addMember(ownerId, groupId, new AddMemberRequest(memberId));

        service.removeMember(memberId, groupId, memberId);

        assertNull(memberships.find(groupId, memberId));
    }

    @Test
    void ownerCannotLeave() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();

        GroupService.Rejected rejected = assertThrows(GroupService.Rejected.class,
            () -> service.removeMember(ownerId, groupId, ownerId));

        assertEquals(GroupService.Rejected.Reason.FORBIDDEN, rejected.getReason());
    }

    @Test
    void memberCannotAddOrRemoveOthers() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();
        service.addMember(ownerId, groupId, new AddMemberRequest(memberId));
        String otherId = saveAccount("other");

        GroupService.Rejected add = assertThrows(GroupService.Rejected.class,
            () -> service.addMember(memberId, groupId, new AddMemberRequest(otherId)));
        GroupService.Rejected remove = assertThrows(GroupService.Rejected.class,
            () -> service.removeMember(memberId, groupId, ownerId));

        assertEquals(GroupService.Rejected.Reason.FORBIDDEN, add.getReason());
        assertEquals(GroupService.Rejected.Reason.FORBIDDEN, remove.getReason());
    }

    @Test
    void outsiderIsForbidden() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();

        GroupService.Rejected rejected = assertThrows(GroupService.Rejected.class,
            () -> service.removeMember(memberId, groupId, ownerId));

        assertEquals(GroupService.Rejected.Reason.FORBIDDEN, rejected.getReason());
    }

    @Test
    void missingGroupOrUserIsNotFound() {
        GroupService.Rejected missingGroup = assertThrows(GroupService.Rejected.class,
            () -> service.addMember(ownerId, UUID.randomUUID().toString(), new AddMemberRequest(memberId)));
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();
        GroupService.Rejected missingUser = assertThrows(GroupService.Rejected.class,
            () -> service.addMember(ownerId, groupId, new AddMemberRequest(UUID.randomUUID().toString())));

        assertEquals(GroupService.Rejected.Reason.NOT_FOUND, missingGroup.getReason());
        assertEquals(GroupService.Rejected.Reason.NOT_FOUND, missingUser.getReason());
    }

    @Test
    void duplicateMemberConflicts() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();
        service.addMember(ownerId, groupId, new AddMemberRequest(memberId));

        GroupService.Rejected rejected = assertThrows(GroupService.Rejected.class,
            () -> service.addMember(ownerId, groupId, new AddMemberRequest(memberId)));

        assertEquals(GroupService.Rejected.Reason.CONFLICT, rejected.getReason());
    }

    @Test
    void listIncludesOwnerAndMember() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();
        service.addMember(ownerId, groupId, new AddMemberRequest(memberId));

        assertEquals(Role.OWNER, service.list(ownerId).getGroups().get(0).getRole());
        assertEquals(Role.MEMBER, service.list(memberId).getGroups().get(0).getRole());
    }

    private String saveAccount(String name) {
        String userId = UUID.randomUUID().toString();
        accounts.save(new Account(userId, name, name + "@example.com", "hash", Instant.now()));
        return userId;
    }

    private static final class ImmediateTransactions implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }

    private static final class MemoryGroups implements GroupRespository {
        private final List<Group> rows = new ArrayList<>();

        @Override
        public void save(Group group) {
            rows.add(group);
        }

        @Override
        public Group findById(String groupId) {
            return rows.stream().filter(row -> row.getGroupId().equals(groupId)).findFirst().orElse(null);
        }

        int size() {
            return rows.size();
        }
    }

    private static final class MemoryMemberships implements MembershipRepository {
        private final List<Membership> rows = new ArrayList<>();

        @Override
        public void save(Membership membership) {
            rows.add(membership);
        }

        @Override
        public void delete(String groupId, String userId) {
            rows.removeIf(row -> row.getGroupId().equals(groupId) && row.getUserId().equals(userId));
        }

        @Override
        public Membership find(String groupId, String userId) {
            return rows.stream()
                .filter(row -> row.getGroupId().equals(groupId) && row.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
        }

        @Override
        public List<Membership> listByUser(String userId) {
            return rows.stream().filter(row -> row.getUserId().equals(userId)).toList();
        }
    }

    private static final class MemoryAccounts implements AccountRepository {
        private final List<Account> rows = new ArrayList<>();

        @Override
        public void save(Account account) {
            rows.add(account);
        }

        @Override
        public Account findByUsername(String username) {
            return rows.stream().filter(row -> row.getUsername().equals(username)).findFirst().orElse(null);
        }

        @Override
        public Account findByEmail(String email) {
            return rows.stream().filter(row -> row.getEmail().equals(email)).findFirst().orElse(null);
        }

        @Override
        public Account findById(String userId) {
            return rows.stream().filter(row -> row.getUserId().equals(userId)).findFirst().orElse(null);
        }
    }
}
