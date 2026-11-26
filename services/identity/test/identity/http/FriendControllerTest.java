package identity.http;

import identity.account.Account;
import identity.account.PlatformRole;
import identity.account.Profile;
import identity.friend.FriendRequest;
import identity.service.FriendService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FriendControllerTest {
    private static final String ANH = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String HUY = "11111111-2222-4333-8444-555555555555";

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        MemoryAccounts accounts = new MemoryAccounts();
        accounts.save(new Account(ANH, "anh", "anh@example.com", "hash", Instant.now()));
        accounts.save(new Account(HUY, "huy", "huy@example.com", "hash", Instant.now()));
        FriendService friends = new FriendService(
            accounts,
            new EmptyProfiles(),
            new MemoryFriends(),
            new MemoryBlocks(),
            new identity.event.EventPublisher() {
                @Override
                public void publishLoggedIn(String userId) {
                }
            }
        );
        mvc = MockMvcBuilders.standaloneSetup(new FriendController(friends))
            .setControllerAdvice(new FriendExceptionHandler(), new AuthExceptionHandler())
            .build();
    }

    @Test
    void requestsShowsIncomingAndOutgoing() throws Exception {
        send(ANH, HUY);

        mvc.perform(get("/friends/requests").requestAttr(SessionFilter.USER_ID, HUY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.incoming", hasSize(1)))
            .andExpect(jsonPath("$.incoming[0].fromUserId").value(ANH))
            .andExpect(jsonPath("$.outgoing", hasSize(0)));
        mvc.perform(get("/friends/requests").requestAttr(SessionFilter.USER_ID, ANH))
            .andExpect(jsonPath("$.outgoing", hasSize(1)))
            .andExpect(jsonPath("$.outgoing[0].toUserId").value(HUY))
            .andExpect(jsonPath("$.incoming", hasSize(0)));
        mvc.perform(get("/friends/requests"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectReturns204AndLeavesNoFriendship() throws Exception {
        send(ANH, HUY);

        mvc.perform(post("/friends/requests/" + ANH + "/reject").requestAttr(SessionFilter.USER_ID, HUY))
            .andExpect(status().isNoContent());

        mvc.perform(get("/friends").requestAttr(SessionFilter.USER_ID, HUY))
            .andExpect(jsonPath("$.friends", hasSize(0)));
        mvc.perform(post("/friends/requests/" + ANH + "/reject").requestAttr(SessionFilter.USER_ID, HUY))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void cancelRemovesOutgoingRequest() throws Exception {
        send(ANH, HUY);

        mvc.perform(delete("/friends/requests/" + HUY).requestAttr(SessionFilter.USER_ID, HUY))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/friends/requests/" + HUY).requestAttr(SessionFilter.USER_ID, ANH))
            .andExpect(status().isNoContent());

        mvc.perform(get("/friends/requests").requestAttr(SessionFilter.USER_ID, HUY))
            .andExpect(jsonPath("$.incoming", hasSize(0)));
    }

    @Test
    void unfriendRemovesFriendshipForBoth() throws Exception {
        send(ANH, HUY);
        mvc.perform(post("/friends/requests/" + ANH + "/accept").requestAttr(SessionFilter.USER_ID, HUY))
            .andExpect(status().isOk());

        mvc.perform(delete("/friends/" + HUY).requestAttr(SessionFilter.USER_ID, ANH))
            .andExpect(status().isNoContent());

        mvc.perform(get("/friends").requestAttr(SessionFilter.USER_ID, ANH))
            .andExpect(jsonPath("$.friends", hasSize(0)));
        mvc.perform(get("/friends").requestAttr(SessionFilter.USER_ID, HUY))
            .andExpect(jsonPath("$.friends", hasSize(0)));
        mvc.perform(delete("/friends/" + HUY).requestAttr(SessionFilter.USER_ID, ANH))
            .andExpect(status().isNotFound());
    }

    @Test
    void blockListUnblockFlow() throws Exception {
        mvc.perform(post("/blocks")
                .requestAttr(SessionFilter.USER_ID, ANH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + HUY + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(HUY));

        mvc.perform(get("/blocks").requestAttr(SessionFilter.USER_ID, ANH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.friends", hasSize(1)))
            .andExpect(jsonPath("$.friends[0].username").value("huy"));

        mvc.perform(delete("/blocks/" + HUY).requestAttr(SessionFilter.USER_ID, ANH))
            .andExpect(status().isNoContent());
        mvc.perform(get("/blocks").requestAttr(SessionFilter.USER_ID, ANH))
            .andExpect(jsonPath("$.friends", hasSize(0)));
        mvc.perform(delete("/blocks/" + HUY).requestAttr(SessionFilter.USER_ID, ANH))
            .andExpect(status().isNotFound());
    }

    @Test
    void blockedUserCannotSendRequestAndSelfBlockFails() throws Exception {
        mvc.perform(post("/blocks")
                .requestAttr(SessionFilter.USER_ID, ANH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + HUY + "\"}"))
            .andExpect(status().isCreated());

        mvc.perform(post("/friends/requests")
                .requestAttr(SessionFilter.USER_ID, HUY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + ANH + "\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(post("/blocks")
                .requestAttr(SessionFilter.USER_ID, ANH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + ANH + "\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/blocks"))
            .andExpect(status().isUnauthorized());
    }

    private void send(String from, String to) throws Exception {
        mvc.perform(post("/friends/requests")
                .requestAttr(SessionFilter.USER_ID, from)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + to + "\"}"))
            .andExpect(status().isCreated());
    }

    private static final class EmptyProfiles implements ProfileRepository {
        @Override
        public Profile find(String userId) {
            return null;
        }

        @Override
        public void save(Profile profile) {
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
        public void updatePlatformRole(String userId, PlatformRole role) {
        }

        @Override
        public void updateBannedAt(String userId, Instant bannedAt) {
        }

        @Override
        public void updatePasswordHash(String userId, String passwordHash) {
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
        public List<String> friendIds(String userId) {
            String prefix = userId + ":";
            return links.stream().filter(link -> link.startsWith(prefix)).map(link -> link.substring(prefix.length())).toList();
        }

        @Override
        public List<FriendRequest> incomingRequests(String userId) {
            return requests.stream().filter(row -> row.getToUserId().equals(userId)).toList();
        }

        @Override
        public List<FriendRequest> outgoingRequests(String userId) {
            return requests.stream().filter(row -> row.getFromUserId().equals(userId)).toList();
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
