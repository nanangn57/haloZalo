package identity.service;

import identity.account.Account;
import identity.account.PlatformRole;
import identity.group.AddMemberRequest;
import identity.group.CreateGroupRequest;
import identity.group.Group;
import identity.group.GroupMemberResponse;
import identity.group.GroupPatch;
import identity.group.GroupResponse;
import identity.group.MemberListResponse;
import identity.group.Membership;
import identity.group.MembershipResponse;
import identity.event.EventPublisher;
import identity.group.GroupRolePolicyRegistry;
import identity.group.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import identity.storage.AccountRepository;
import identity.storage.DuplicateKeyException;
import identity.storage.GroupRespository;
import identity.storage.MembershipRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupServiceTest {
    private MemoryGroups groups;
    private MemoryMemberships memberships;
    private MemoryAccounts accounts;
    private GroupService service;
    private MemoryEvents events;
    private String ownerId;
    private String memberId;

    @BeforeEach
    void setUp() {
        groups = new MemoryGroups();
        memberships = new MemoryMemberships();
        accounts = new MemoryAccounts();
        events = new MemoryEvents();
        service = new GroupService(groups, memberships, accounts, new GroupRolePolicyRegistry(), new ImmediateTransactions(), events);
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
        assertEquals(List.of(created.getGroupId()), events.groupIds);
        assertEquals(List.of(ownerId), events.ownerIds);
    }

    @Test
    void addingAMemberPublishesWhoAddedWhom() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();

        service.addMember(ownerId.toUpperCase(), groupId, new AddMemberRequest(memberId));

        assertEquals(List.of("added " + groupId + " " + ownerId + " " + memberId), events.changes);
    }

    @Test
    void removingAMemberPublishesRemovedAndLeavingPublishesLeft() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();
        String otherId = saveAccount("other");
        service.addMember(ownerId, groupId, new AddMemberRequest(memberId));
        service.addMember(ownerId, groupId, new AddMemberRequest(otherId));
        events.changes.clear();

        service.removeMember(ownerId, groupId, memberId);
        service.removeMember(otherId, groupId, otherId);

        assertEquals(List.of(
            "removed " + groupId + " " + ownerId + " " + memberId,
            "left " + groupId + " " + otherId), events.changes);
    }

    @Test
    void transferringOwnershipPublishesFromAndTo() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();
        service.addMember(ownerId, groupId, new AddMemberRequest(memberId));
        events.changes.clear();

        service.transferOwner(ownerId, groupId, memberId);

        assertEquals(List.of("transferred " + groupId + " " + ownerId + " " + memberId), events.changes);
    }

    @Test
    void dissolvingPublishesDissolvedAndSoDoesTheSoleOwnerLeaving() {
        String first = service.create(ownerId, new CreateGroupRequest("one")).getGroupId();
        String second = service.create(ownerId, new CreateGroupRequest("two")).getGroupId();

        service.dissolve(ownerId, first);
        service.removeMember(ownerId, second, ownerId);

        assertEquals(List.of("dissolved " + first + " " + ownerId, "dissolved " + second + " " + ownerId), events.changes);
    }

    @Test
    void rejectedChangesPublishNothing() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();
        service.addMember(ownerId, groupId, new AddMemberRequest(memberId));
        String otherId = saveAccount("other");
        events.changes.clear();

        assertThrows(GroupService.Rejected.class, () -> service.addMember(memberId, groupId, new AddMemberRequest(otherId)));
        assertThrows(GroupService.Rejected.class, () -> service.addMember(ownerId, groupId, new AddMemberRequest(memberId)));
        assertThrows(GroupService.Rejected.class, () -> service.removeMember(memberId, groupId, ownerId));
        assertThrows(GroupService.Rejected.class, () -> service.removeMember(ownerId, groupId, ownerId));
        assertThrows(GroupService.Rejected.class, () -> service.transferOwner(memberId, groupId, ownerId));
        assertThrows(GroupService.Rejected.class, () -> service.dissolve(memberId, groupId));

        assertTrue(events.changes.isEmpty());
    }

    @Test
    void createAllowsEmptyNameForMemberBasedTitle() {
        GroupResponse created = service.create(ownerId, new CreateGroupRequest(""));
        GroupResponse blank = service.create(ownerId, new CreateGroupRequest("  "));

        assertEquals("", created.getName());
        assertEquals("", blank.getName());
        assertEquals(2, events.groupIds.size());
    }

    @Test
    void createAddsInitialMembersAndSkipsOwnerDuplicate() {
        String other = saveAccount("other");
        GroupResponse created = service.create(
            ownerId,
            new CreateGroupRequest("nhom", List.of(memberId, ownerId, memberId, other))
        );

        assertEquals(Role.MEMBER, memberships.find(created.getGroupId(), memberId).getRole());
        assertEquals(Role.MEMBER, memberships.find(created.getGroupId(), other).getRole());
        assertEquals(Role.OWNER, memberships.find(created.getGroupId(), ownerId).getRole());
        assertEquals(3, memberships.listByGroup(created.getGroupId()).size());
        assertEquals(2, events.changes.stream().filter(row -> row.startsWith("added ")).count());
    }

    @Test
    void createRejectsUnknownInitialMember() {
        GroupService.Rejected rejected = assertThrows(
            GroupService.Rejected.class,
            () -> service.create(
                ownerId,
                new CreateGroupRequest("nhom", List.of(UUID.randomUUID().toString()))
            )
        );

        assertEquals(GroupService.Rejected.Reason.NOT_FOUND, rejected.getReason());
        assertEquals(0, groups.size());
    }

    @Test
    void createRejectsNameLongerThanMax() {
        String tooLong = "x".repeat(65);
        GroupService.Rejected rejected = assertThrows(GroupService.Rejected.class,
            () -> service.create(ownerId, new CreateGroupRequest(tooLong)));

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
    void ownerCannotLeaveWhileOthersRemain() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();
        service.addMember(ownerId, groupId, new AddMemberRequest(memberId));

        GroupService.Rejected rejected = assertThrows(GroupService.Rejected.class,
            () -> service.removeMember(ownerId, groupId, ownerId));

        assertEquals(GroupService.Rejected.Reason.FORBIDDEN, rejected.getReason());
        assertEquals(Role.OWNER, memberships.find(groupId, ownerId).getRole());
    }

    @Test
    void soleOwnerLeavingDissolvesTheGroup() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();

        service.removeMember(ownerId, groupId, ownerId);

        assertNull(groups.findById(groupId));
        assertTrue(memberships.listByGroup(groupId).isEmpty());
    }

    @Test
    void ownerCanLeaveAfterTransferringOwnership() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();
        service.addMember(ownerId, groupId, new AddMemberRequest(memberId));
        service.transferOwner(ownerId, groupId, memberId);

        service.removeMember(ownerId, groupId, ownerId);

        assertNull(memberships.find(groupId, ownerId));
        assertEquals(Role.OWNER, memberships.find(groupId, memberId).getRole());
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
    void duplicateInsertConflicts() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();
        GroupService racing = new GroupService(
            groups,
            new SaveRejectsDuplicate(memberships),
            accounts,
            new GroupRolePolicyRegistry(),
            new ImmediateTransactions(),
            events
        );

        GroupService.Rejected rejected = assertThrows(GroupService.Rejected.class,
            () -> racing.addMember(ownerId, groupId, new AddMemberRequest(memberId)));

        assertEquals(GroupService.Rejected.Reason.CONFLICT, rejected.getReason());
    }

    @Test
    void upperCaseIdsMatchTheStoredGroup() {
        GroupResponse created = service.create(ownerId.toUpperCase(), new CreateGroupRequest("nhom"));
        String groupId = created.getGroupId();

        MembershipResponse added = service.addMember(
            ownerId.toUpperCase(),
            groupId.toUpperCase(),
            new AddMemberRequest(memberId.toUpperCase())
        );

        assertEquals(ownerId, created.getOwnerId());
        assertEquals(groupId, added.getGroupId());
        assertEquals(memberId, added.getUserId());
        assertEquals(Role.MEMBER, service.list(memberId.toUpperCase()).getGroups().get(0).getRole());

        service.removeMember(memberId.toUpperCase(), groupId.toUpperCase(), memberId.toUpperCase());
        assertNull(memberships.find(groupId, memberId));
    }

    @Test
    void listIncludesOwnerAndMember() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();
        service.addMember(ownerId, groupId, new AddMemberRequest(memberId));

        assertEquals(Role.OWNER, service.list(ownerId).getGroups().get(0).getRole());
        assertEquals(Role.MEMBER, service.list(memberId).getGroups().get(0).getRole());
    }

    @Test
    void memberSeesOwnerAndSelf() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();
        service.addMember(ownerId, groupId, new AddMemberRequest(memberId));

        MemberListResponse listed = service.listMembers(memberId, groupId);
        Map<String, GroupMemberResponse> byUser = new HashMap<>();
        for (GroupMemberResponse member : listed.getMembers()) {
            byUser.put(member.getUserId(), member);
        }

        assertEquals(2, byUser.size());
        assertEquals("owner", byUser.get(ownerId).getUsername());
        assertEquals(Role.OWNER, byUser.get(ownerId).getRole());
        assertEquals("member", byUser.get(memberId).getUsername());
        assertEquals(Role.MEMBER, byUser.get(memberId).getRole());
    }

    @Test
    void outsiderCannotListMembers() {
        String groupId = service.create(ownerId, new CreateGroupRequest("nhom")).getGroupId();
        String stranger = saveAccount("stranger");

        GroupService.Rejected rejected = assertThrows(
            GroupService.Rejected.class,
            () -> service.listMembers(stranger, groupId)
        );

        assertEquals(GroupService.Rejected.Reason.FORBIDDEN, rejected.getReason());
    }

    @Test
    void listMembersMissingGroup() {
        GroupService.Rejected rejected = assertThrows(
            GroupService.Rejected.class,
            () -> service.listMembers(ownerId, UUID.randomUUID().toString())
        );

        assertEquals(GroupService.Rejected.Reason.NOT_FOUND, rejected.getReason());
    }

    @Test
    void listMembersRejectsBadGroupId() {
        GroupService.Rejected rejected = assertThrows(
            GroupService.Rejected.class,
            () -> service.listMembers(ownerId, "not-a-uuid")
        );

        assertEquals(GroupService.Rejected.Reason.VALIDATION, rejected.getReason());
    }

    @Test
    void roleEnumHasOnlyOwnerAndMember() {
        assertEquals(List.of(Role.OWNER, Role.MEMBER), List.of(Role.values()));
    }

    @Test
    void transferOwnerSwapsRolesAndGroupOwner() {
        GroupResponse group = service.create(ownerId, new CreateGroupRequest("nhom"));
        service.addMember(ownerId, group.getGroupId(), new AddMemberRequest(memberId));

        MembershipResponse transferred = service.transferOwner(ownerId, group.getGroupId(), memberId);

        assertEquals(Role.OWNER, transferred.getRole());
        assertEquals(memberId, transferred.getUserId());
        assertEquals(Role.OWNER, memberships.find(group.getGroupId(), memberId).getRole());
        assertEquals(Role.MEMBER, memberships.find(group.getGroupId(), ownerId).getRole());
        assertEquals(memberId, groups.findById(group.getGroupId()).getGroupOwnerId());
        assertEquals(Role.MEMBER, service.list(ownerId).getGroups().get(0).getRole());
    }

    @Test
    void formerOwnerLosesOwnerPowersAfterTransfer() {
        GroupResponse group = service.create(ownerId, new CreateGroupRequest("nhom"));
        service.addMember(ownerId, group.getGroupId(), new AddMemberRequest(memberId));
        service.transferOwner(ownerId, group.getGroupId(), memberId);
        String extra = saveAccount("extra");

        GroupService.Rejected add = assertThrows(GroupService.Rejected.class,
            () -> service.addMember(ownerId, group.getGroupId(), new AddMemberRequest(extra)));
        GroupService.Rejected dissolve = assertThrows(GroupService.Rejected.class,
            () -> service.dissolve(ownerId, group.getGroupId()));

        assertEquals(GroupService.Rejected.Reason.FORBIDDEN, add.getReason());
        assertEquals(GroupService.Rejected.Reason.FORBIDDEN, dissolve.getReason());
    }

    @Test
    void transferOwnerRejectsNonOwnerNonMemberSelfAndBadIds() {
        GroupResponse group = service.create(ownerId, new CreateGroupRequest("nhom"));
        service.addMember(ownerId, group.getGroupId(), new AddMemberRequest(memberId));
        String stranger = saveAccount("stranger");

        GroupService.Rejected byMember = assertThrows(GroupService.Rejected.class,
            () -> service.transferOwner(memberId, group.getGroupId(), memberId));
        GroupService.Rejected toStranger = assertThrows(GroupService.Rejected.class,
            () -> service.transferOwner(ownerId, group.getGroupId(), stranger));
        GroupService.Rejected toSelf = assertThrows(GroupService.Rejected.class,
            () -> service.transferOwner(ownerId, group.getGroupId(), ownerId));
        GroupService.Rejected badId = assertThrows(GroupService.Rejected.class,
            () -> service.transferOwner(ownerId, group.getGroupId(), "nope"));
        GroupService.Rejected missingGroup = assertThrows(GroupService.Rejected.class,
            () -> service.transferOwner(ownerId, UUID.randomUUID().toString(), memberId));

        assertEquals(GroupService.Rejected.Reason.FORBIDDEN, byMember.getReason());
        assertEquals(GroupService.Rejected.Reason.NOT_FOUND, toStranger.getReason());
        assertEquals(GroupService.Rejected.Reason.VALIDATION, toSelf.getReason());
        assertEquals(GroupService.Rejected.Reason.VALIDATION, badId.getReason());
        assertEquals(GroupService.Rejected.Reason.NOT_FOUND, missingGroup.getReason());
        assertEquals(Role.OWNER, memberships.find(group.getGroupId(), ownerId).getRole());
    }

    @Test
    void ownerDissolvesGroupAndMemberships() {
        GroupResponse group = service.create(ownerId, new CreateGroupRequest("nhom"));
        service.addMember(ownerId, group.getGroupId(), new AddMemberRequest(memberId));

        service.dissolve(ownerId, group.getGroupId());

        assertNull(groups.findById(group.getGroupId()));
        assertTrue(memberships.listByGroup(group.getGroupId()).isEmpty());
        assertTrue(service.list(memberId).getGroups().isEmpty());
    }

    @Test
    void dissolveIsOwnerOnly() {
        GroupResponse group = service.create(ownerId, new CreateGroupRequest("nhom"));
        service.addMember(ownerId, group.getGroupId(), new AddMemberRequest(memberId));
        String stranger = saveAccount("stranger");

        GroupService.Rejected byMember = assertThrows(GroupService.Rejected.class,
            () -> service.dissolve(memberId, group.getGroupId()));
        GroupService.Rejected byStranger = assertThrows(GroupService.Rejected.class,
            () -> service.dissolve(stranger, group.getGroupId()));
        GroupService.Rejected missing = assertThrows(GroupService.Rejected.class,
            () -> service.dissolve(ownerId, UUID.randomUUID().toString()));

        assertEquals(GroupService.Rejected.Reason.FORBIDDEN, byMember.getReason());
        assertEquals(GroupService.Rejected.Reason.FORBIDDEN, byStranger.getReason());
        assertEquals(GroupService.Rejected.Reason.NOT_FOUND, missing.getReason());
        assertNotNull(groups.findById(group.getGroupId()));
        assertEquals(2, memberships.listByGroup(group.getGroupId()).size());
    }

    @Test
    void ownerUpdatesNameDescriptionAndAvatar() {
        GroupResponse group = service.create(ownerId, new CreateGroupRequest("nhom"));
        String avatar = "http://localhost/avatar.png";
        GroupPatch patch = GroupPatch.from(json("{\"name\":\"nhom moi\",\"description\":\"mo ta\",\"avatar\":\"" + avatar + "\"}"));

        GroupResponse updated = service.update(ownerId, group.getGroupId(), patch);

        assertEquals("nhom moi", updated.getName());
        assertEquals("mo ta", updated.getDescription());
        assertEquals(avatar, updated.getAvatar());
        assertEquals(Role.OWNER, updated.getRole());
        Group stored = groups.findById(group.getGroupId());
        assertEquals("nhom moi", stored.getGroupName());
        assertEquals("mo ta", stored.getGroupDescription());
        assertEquals(avatar, stored.getGroupAvatar());
        assertEquals(ownerId, stored.getGroupOwnerId());
    }

    @Test
    void updateKeepsFieldsNotInThePatchAndClearsNulls() {
        GroupResponse group = service.create(ownerId, new CreateGroupRequest("nhom"));
        service.update(ownerId, group.getGroupId(), GroupPatch.from(json("{\"description\":\"mo ta\",\"avatar\":\"a.png\"}")));

        service.update(ownerId, group.getGroupId(), GroupPatch.from(json("{\"avatar\":null}")));

        Group stored = groups.findById(group.getGroupId());
        assertEquals("nhom", stored.getGroupName());
        assertEquals("mo ta", stored.getGroupDescription());
        assertNull(stored.getGroupAvatar());
    }

    @Test
    void updateRejectsInvalidValuesEmptyPatchAndNonOwner() {
        GroupResponse group = service.create(ownerId, new CreateGroupRequest("nhom"));
        service.addMember(ownerId, group.getGroupId(), new AddMemberRequest(memberId));

        GroupResponse cleared = service.update(
            ownerId,
            group.getGroupId(),
            GroupPatch.from(json("{\"name\":\" \"}"))
        );
        GroupService.Rejected longName = assertThrows(GroupService.Rejected.class,
            () -> service.update(ownerId, group.getGroupId(), GroupPatch.from(json("{\"name\":\"" + "x".repeat(65) + "\"}"))));
        GroupService.Rejected longDescription = assertThrows(GroupService.Rejected.class,
            () -> service.update(ownerId, group.getGroupId(), GroupPatch.from(json("{\"description\":\"" + "x".repeat(281) + "\"}"))));
        GroupService.Rejected empty = assertThrows(GroupService.Rejected.class,
            () -> service.update(ownerId, group.getGroupId(), GroupPatch.from(json("{}"))));
        GroupService.Rejected byMember = assertThrows(GroupService.Rejected.class,
            () -> service.update(memberId, group.getGroupId(), GroupPatch.from(json("{\"name\":\"hack\"}"))));

        assertEquals("", cleared.getName());
        assertEquals(GroupService.Rejected.Reason.VALIDATION, longName.getReason());
        assertEquals(GroupService.Rejected.Reason.VALIDATION, longDescription.getReason());
        assertEquals(GroupService.Rejected.Reason.VALIDATION, empty.getReason());
        assertEquals(GroupService.Rejected.Reason.FORBIDDEN, byMember.getReason());
        assertEquals("", groups.findById(group.getGroupId()).getGroupName());
    }

    private static JsonNode json(String text) {
        try {
            return new ObjectMapper().readTree(text);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
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

        @Override
        public void update(Group group) {
            rows.removeIf(row -> row.getGroupId().equals(group.getGroupId()));
            rows.add(group);
        }

        @Override
        public void delete(String groupId) {
            rows.removeIf(row -> row.getGroupId().equals(groupId));
        }

        int size() {
            return rows.size();
        }
    }

    private static final class SaveRejectsDuplicate implements MembershipRepository {
        private final MembershipRepository delegate;

        private SaveRejectsDuplicate(MembershipRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public void save(Membership membership) {
            throw new DuplicateKeyException("Membership already exists", new IllegalStateException("duplicate"));
        }

        @Override
        public void delete(String groupId, String userId) {
            delegate.delete(groupId, userId);
        }

        @Override
        public void deleteByGroup(String groupId) {
            delegate.deleteByGroup(groupId);
        }

        @Override
        public Membership find(String groupId, String userId) {
            return delegate.find(groupId, userId);
        }

        @Override
        public List<Membership> listByUser(String userId) {
            return delegate.listByUser(userId);
        }

        @Override
        public List<Membership> listByGroup(String groupId) {
            return delegate.listByGroup(groupId);
        }

        @Override
        public void updateRole(String groupId, String userId, Role role) {
            delegate.updateRole(groupId, userId, role);
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
        public void deleteByGroup(String groupId) {
            rows.removeIf(row -> row.getGroupId().equals(groupId));
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

        @Override
        public List<Membership> listByGroup(String groupId) {
            return rows.stream().filter(row -> row.getGroupId().equals(groupId)).toList();
        }

        @Override
        public void updateRole(String groupId, String userId, Role role) {
            Membership current = find(groupId, userId);
            if (current == null) {
                return;
            }
            delete(groupId, userId);
            save(new Membership(groupId, userId, current.getJoinedAt(), role));
        }
    }

    private static final class MemoryEvents implements EventPublisher {
        private final List<String> groupIds = new ArrayList<>();
        private final List<String> ownerIds = new ArrayList<>();
        private final List<String> changes = new ArrayList<>();

        @Override
        public void publishLoggedIn(String userId) {
        }

        @Override
        public void publishGroupCreated(String groupId, String ownerId) {
            publishGroupCreated(groupId, ownerId, 1);
        }

        @Override
        public void publishGroupCreated(String groupId, String ownerId, int memberCount) {
            groupIds.add(groupId);
            ownerIds.add(ownerId);
        }

        @Override
        public void publishMemberAdded(String groupId, String actorId, String memberId) {
            changes.add("added " + groupId + " " + actorId + " " + memberId);
        }

        @Override
        public void publishMemberRemoved(String groupId, String actorId, String memberId) {
            changes.add("removed " + groupId + " " + actorId + " " + memberId);
        }

        @Override
        public void publishMemberLeft(String groupId, String memberId) {
            changes.add("left " + groupId + " " + memberId);
        }

        @Override
        public void publishOwnerTransferred(String groupId, String fromUserId, String toUserId) {
            changes.add("transferred " + groupId + " " + fromUserId + " " + toUserId);
        }

        @Override
        public void publishGroupDissolved(String groupId, String actorId) {
            changes.add("dissolved " + groupId + " " + actorId);
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

        @Override
        public void updateBannedAt(String userId, java.time.Instant bannedAt) {
            for (int i = 0; i < rows.size(); i++) {
                Account current = rows.get(i);
                if (current.getUserId().equals(userId)) {
                    rows.set(i, current.withBannedAt(bannedAt));
                    return;
                }
            }
        }

        @Override
        public void updatePasswordHash(String userId, String passwordHash) {
            for (int i = 0; i < rows.size(); i++) {
                Account current = rows.get(i);
                if (current.getUserId().equals(userId)) {
                    rows.set(i, current.withPasswordHash(passwordHash));
                    return;
                }
            }
        }

        @Override
        public void updatePlatformRole(String userId, PlatformRole role) {
            for (int i = 0; i < rows.size(); i++) {
                Account current = rows.get(i);
                if (current.getUserId().equals(userId)) {
                    rows.set(i, current.withPlatformRole(role));
                    return;
                }
            }
        }

    }
}
