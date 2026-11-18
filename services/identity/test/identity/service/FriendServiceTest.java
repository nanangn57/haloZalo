package identity.service;

import identity.account.Account;
import identity.account.Profile;
import identity.friend.FriendRequest;
import identity.friend.FriendView;
import identity.storage.AccountRepository;
import identity.storage.DuplicateKeyException;
import identity.storage.FriendRepository;
import identity.storage.ProfileRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FriendServiceTest {
    private static final String ANH = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String HUY = "11111111-2222-4333-8444-555555555555";

    private MemoryFriends stored;
    private FriendService service;

    @BeforeEach
    void setUp() {
        MemoryAccounts accounts = new MemoryAccounts();
        accounts.save(new Account(ANH, "anh", "anh@example.com", "hash", Instant.now()));
        accounts.save(new Account(HUY, "huy", "huy@example.com", "hash", Instant.now()));
        MemoryProfiles profiles = new MemoryProfiles();
        profiles.save(new Profile(HUY, "Huy", null));
        stored = new MemoryFriends();
        service = new FriendService(accounts, profiles, stored);
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
}
