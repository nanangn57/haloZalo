package identity.service;

import identity.account.Account;
import identity.account.PlatformRole;
import identity.account.Profile;
import identity.friend.FriendRequest;
import identity.friend.FriendRequestList;
import identity.friend.FriendView;
import identity.storage.AccountRepository;
import identity.storage.BlockRepository;
import identity.storage.DuplicateKeyException;
import identity.storage.FriendRepository;
import identity.storage.ProfileRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendServiceTest {
    private static final String ANH = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String HUY = "11111111-2222-4333-8444-555555555555";

    private static final String LAN = "33333333-4444-4555-8666-777777777777";

    private MemoryFriends stored;
    private MemoryBlocks blocks;
    private FriendService service;

    @BeforeEach
    void setUp() {
        MemoryAccounts accounts = new MemoryAccounts();
        accounts.save(new Account(ANH, "anh", "anh@example.com", "hash", Instant.now()));
        accounts.save(new Account(HUY, "huy", "huy@example.com", "hash", Instant.now()));
        accounts.save(new Account(LAN, "lan", "lan@example.com", "hash", Instant.now()));
        MemoryProfiles profiles = new MemoryProfiles();
        profiles.save(new Profile(HUY, "Huy", null));
        stored = new MemoryFriends();
        blocks = new MemoryBlocks();
        service = new FriendService(accounts, profiles, stored, blocks, new identity.event.EventPublisher() {
            @Override
            public void publishLoggedIn(String userId) {
            }
        });
    }

    @Test
    void sendAcceptAndList() {
        FriendRequest request = service.send(ANH, HUY);
        assertEquals(ANH, request.getFromUserId());
        assertEquals(HUY, request.getToUserId());

        FriendView accepted = service.accept(HUY, ANH);
        assertEquals(ANH, accepted.getUserId());
        assertNull(accepted.getDisplayName());

        List<FriendView> friends = service.list(ANH);
        assertEquals(1, friends.size());
        assertEquals("huy", friends.get(0).getUsername());
        assertEquals("Huy", friends.get(0).getDisplayName());
    }

    @Test
    void selfUnknownAndDuplicateFail() {
        FriendService.Rejected self = assertThrows(FriendService.Rejected.class, () -> service.send(ANH, ANH));
        assertEquals(FriendService.Rejected.Reason.VALIDATION, self.getReason());

        FriendService.Rejected missing = assertThrows(
            FriendService.Rejected.class,
            () -> service.send(ANH, "99999999-8888-4777-8666-555555555555")
        );
        assertEquals(FriendService.Rejected.Reason.NOT_FOUND, missing.getReason());

        service.send(ANH, HUY);
        FriendService.Rejected again = assertThrows(FriendService.Rejected.class, () -> service.send(HUY, ANH));
        assertEquals(FriendService.Rejected.Reason.CONFLICT, again.getReason());

        FriendService.Rejected noRequest = assertThrows(
            FriendService.Rejected.class,
            () -> service.accept(ANH, "99999999-8888-4777-8666-555555555555")
        );
        assertEquals(FriendService.Rejected.Reason.NOT_FOUND, noRequest.getReason());
    }

    @Test
    void requestsListsIncomingAndOutgoing() {
        service.send(ANH, HUY);
        service.send(LAN, ANH);

        FriendRequestList forAnh = service.requests(ANH);
        FriendRequestList forHuy = service.requests(HUY);

        assertEquals(1, forAnh.getOutgoing().size());
        assertEquals(HUY, forAnh.getOutgoing().get(0).getToUserId());
        assertEquals(1, forAnh.getIncoming().size());
        assertEquals(LAN, forAnh.getIncoming().get(0).getFromUserId());
        assertEquals(1, forHuy.getIncoming().size());
        assertEquals(ANH, forHuy.getIncoming().get(0).getFromUserId());
        assertTrue(forHuy.getOutgoing().isEmpty());
    }

    @Test
    void requestsRejectsInvalidCaller() {
        FriendService.Rejected rejected = assertThrows(FriendService.Rejected.class, () -> service.requests("nope"));

        assertEquals(FriendService.Rejected.Reason.VALIDATION, rejected.getReason());
    }

    @Test
    void rejectDeletesIncomingRequestWithoutFriendship() {
        service.send(ANH, HUY);

        service.reject(HUY, ANH);

        assertNull(stored.findRequest(ANH, HUY));
        assertFalse(stored.areFriends(ANH, HUY));
        assertTrue(service.list(HUY).isEmpty());
        assertTrue(service.requests(HUY).getIncoming().isEmpty());
    }

    @Test
    void rejectFailsWhenThereIsNoRequestOrIdIsBad() {
        FriendService.Rejected none = assertThrows(FriendService.Rejected.class, () -> service.reject(HUY, ANH));
        FriendService.Rejected bad = assertThrows(FriendService.Rejected.class, () -> service.reject(HUY, "nope"));
        service.send(ANH, HUY);
        FriendService.Rejected wrongSide = assertThrows(FriendService.Rejected.class, () -> service.reject(ANH, HUY));

        assertEquals(FriendService.Rejected.Reason.NOT_FOUND, none.getReason());
        assertEquals(FriendService.Rejected.Reason.VALIDATION, bad.getReason());
        assertEquals(FriendService.Rejected.Reason.NOT_FOUND, wrongSide.getReason());
        assertNotNull(stored.findRequest(ANH, HUY));
    }

    @Test
    void cancelDeletesOutgoingRequestOnly() {
        service.send(ANH, HUY);

        FriendService.Rejected byRecipient = assertThrows(FriendService.Rejected.class, () -> service.cancel(HUY, ANH));
        service.cancel(ANH, HUY);
        FriendService.Rejected again = assertThrows(FriendService.Rejected.class, () -> service.cancel(ANH, HUY));

        assertEquals(FriendService.Rejected.Reason.NOT_FOUND, byRecipient.getReason());
        assertEquals(FriendService.Rejected.Reason.NOT_FOUND, again.getReason());
        assertNull(stored.findRequest(ANH, HUY));
    }

    @Test
    void unfriendRemovesBothDirections() {
        service.send(ANH, HUY);
        service.accept(HUY, ANH);

        service.unfriend(ANH, HUY);

        assertFalse(stored.areFriends(ANH, HUY));
        assertFalse(stored.areFriends(HUY, ANH));
        assertTrue(service.list(ANH).isEmpty());
        assertTrue(service.list(HUY).isEmpty());
    }

    @Test
    void unfriendFailsForNonFriend() {
        FriendService.Rejected rejected = assertThrows(FriendService.Rejected.class, () -> service.unfriend(ANH, HUY));

        assertEquals(FriendService.Rejected.Reason.NOT_FOUND, rejected.getReason());
    }

    @Test
    void blockHidesFriendshipAndRequestsAndIsListed() {
        service.send(ANH, HUY);
        service.accept(HUY, ANH);
        service.send(LAN, ANH);

        FriendView blocked = service.block(ANH, HUY);
        service.block(ANH, LAN);

        assertEquals(HUY, blocked.getUserId());
        assertEquals("Huy", blocked.getDisplayName());
        assertFalse(stored.areFriends(ANH, HUY));
        assertFalse(stored.areFriends(HUY, ANH));
        assertNull(stored.findRequest(LAN, ANH));
        List<FriendView> listed = service.listBlocked(ANH);
        assertEquals(2, listed.size());
        assertTrue(service.listBlocked(HUY).isEmpty());
    }

    @Test
    void blockTwiceIsIdempotent() {
        service.block(ANH, HUY);
        service.block(ANH, HUY);

        assertEquals(1, service.listBlocked(ANH).size());
    }

    @Test
    void areBlockedIsTrueInBothDirections() {
        assertFalse(service.areBlocked(ANH, HUY));

        service.block(ANH, HUY);

        assertTrue(service.areBlocked(ANH, HUY));
        assertTrue(service.areBlocked(HUY, ANH));
        assertFalse(service.areBlocked(ANH, LAN));
    }

    @Test
    void blockedUsersCannotSendRequestsEitherWay() {
        service.block(ANH, HUY);

        FriendService.Rejected blockerSends = assertThrows(FriendService.Rejected.class, () -> service.send(ANH, HUY));
        FriendService.Rejected blockedSends = assertThrows(FriendService.Rejected.class, () -> service.send(HUY, ANH));

        assertEquals(FriendService.Rejected.Reason.FORBIDDEN, blockerSends.getReason());
        assertEquals(FriendService.Rejected.Reason.FORBIDDEN, blockedSends.getReason());
        assertNull(stored.findRequest(HUY, ANH));
    }

    @Test
    void blockRejectsSelfUnknownAndBadId() {
        FriendService.Rejected self = assertThrows(FriendService.Rejected.class, () -> service.block(ANH, ANH));
        FriendService.Rejected unknown = assertThrows(FriendService.Rejected.class,
            () -> service.block(ANH, "99999999-8888-4777-8666-555555555555"));
        FriendService.Rejected bad = assertThrows(FriendService.Rejected.class, () -> service.block(ANH, null));

        assertEquals(FriendService.Rejected.Reason.VALIDATION, self.getReason());
        assertEquals(FriendService.Rejected.Reason.NOT_FOUND, unknown.getReason());
        assertEquals(FriendService.Rejected.Reason.VALIDATION, bad.getReason());
    }

    @Test
    void unblockRemovesBlockAndAllowsRequestsAgain() {
        service.block(ANH, HUY);

        service.unblock(ANH, HUY);

        assertFalse(service.areBlocked(ANH, HUY));
        assertTrue(service.listBlocked(ANH).isEmpty());
        service.send(ANH, HUY);
        assertNotNull(stored.findRequest(ANH, HUY));
    }

    @Test
    void unblockFailsWhenNotBlocked() {
        FriendService.Rejected rejected = assertThrows(FriendService.Rejected.class, () -> service.unblock(ANH, HUY));

        assertEquals(FriendService.Rejected.Reason.NOT_FOUND, rejected.getReason());
    }

    private static final class MemoryAccounts implements AccountRepository {
        private final List<Account> rows = new ArrayList<>();

        @Override
        public void save(Account account) {
            rows.add(account);
        }

        @Override
        public Account findByUsername(String username) {
            return null;
        }

        @Override
        public Account findByEmail(String email) {
            return null;
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

    private static final class MemoryProfiles implements ProfileRepository {
        private final List<Profile> rows = new ArrayList<>();

        @Override
        public Profile find(String userId) {
            return rows.stream().filter(row -> row.getUserId().equals(userId)).findFirst().orElse(null);
        }

        @Override
        public void save(Profile profile) {
            rows.add(profile);
        }
    }

    private static final class MemoryFriends implements FriendRepository {
        private final List<FriendRequest> requests = new ArrayList<>();
        private final List<String> links = new ArrayList<>();

        @Override
        public void saveRequest(FriendRequest request) {
            if (findRequest(request.getFromUserId(), request.getToUserId()) != null) {
                throw new DuplicateKeyException("duplicate", new IllegalStateException("duplicate"));
            }
            requests.add(request);
        }

        @Override
        public FriendRequest findRequest(String fromUserId, String toUserId) {
            return requests.stream()
                .filter(row -> row.getFromUserId().equals(fromUserId) && row.getToUserId().equals(toUserId))
                .findFirst()
                .orElse(null);
        }

        @Override
        public void deleteRequest(String fromUserId, String toUserId) {
            requests.removeIf(row -> row.getFromUserId().equals(fromUserId) && row.getToUserId().equals(toUserId));
        }

        @Override
        public boolean areFriends(String userId, String friendId) {
            return links.contains(userId + ":" + friendId);
        }

        @Override
        public void saveFriendship(String userId, String friendId) {
            links.add(userId + ":" + friendId);
        }

        @Override
        public void deleteFriendship(String userId, String friendId) {
            links.remove(userId + ":" + friendId);
        }

        @Override
        public List<FriendRequest> incomingRequests(String userId) {
            return requests.stream().filter(row -> row.getToUserId().equals(userId)).toList();
        }

        @Override
        public List<FriendRequest> outgoingRequests(String userId) {
            return requests.stream().filter(row -> row.getFromUserId().equals(userId)).toList();
        }

        @Override
        public List<String> friendIds(String userId) {
            String prefix = userId + ":";
            List<String> ids = new ArrayList<>();
            for (String link : links) {
                if (link.startsWith(prefix)) {
                    ids.add(link.substring(prefix.length()));
                }
            }
            return ids;
        }
    }

    private static final class MemoryBlocks implements BlockRepository {
        private final List<String> rows = new ArrayList<>();

        @Override
        public void save(String blockerId, String blockedId) {
            if (!exists(blockerId, blockedId)) {
                rows.add(blockerId + ":" + blockedId);
            }
        }

        @Override
        public void delete(String blockerId, String blockedId) {
            rows.remove(blockerId + ":" + blockedId);
        }

        @Override
        public boolean exists(String blockerId, String blockedId) {
            return rows.contains(blockerId + ":" + blockedId);
        }

        @Override
        public List<String> blockedIds(String blockerId) {
            String prefix = blockerId + ":";
            return rows.stream().filter(row -> row.startsWith(prefix)).map(row -> row.substring(prefix.length())).toList();
        }
    }
}
