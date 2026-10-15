package http;

import account.Account;
import com.jayway.jsonpath.JsonPath;
import group.Group;
import group.Membership;
import group.OwnerMemberPolicyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import service.GroupService;
import storage.AccountRepository;
import storage.GroupRespository;
import storage.MembershipRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupControllerTest {
    private MockMvc mvc;
    private MemoryAccounts accounts;
    private String ownerId;
    private String memberId;

    @BeforeEach
    void setUp() {
        accounts = new MemoryAccounts();
        GroupService groups = new GroupService(
            new MemoryGroups(),
            new MemoryMemberships(),
            accounts,
            new OwnerMemberPolicyRegistry(),
            new ImmediateTransactions()
        );
        mvc = MockMvcBuilders.standaloneSetup(new GroupController(groups))
            .setControllerAdvice(new GroupExceptionHandler(), new AuthExceptionHandler())
            .build();
        ownerId = saveAccount("owner");
        memberId = saveAccount("member");
    }

    @Test
    void createReturnsOwner() throws Exception {
        mvc.perform(post("/groups")
                .requestAttr(SessionFilter.USER_ID, ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"nhom\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("nhom"))
            .andExpect(jsonPath("$.ownerId").value(ownerId))
            .andExpect(jsonPath("$.role").value("OWNER"))
            .andExpect(jsonPath("$.groupId").isNotEmpty());
    }

    @Test
    void createRejectsEmptyName() throws Exception {
        mvc.perform(post("/groups")
                .requestAttr(SessionFilter.USER_ID, ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void addRemoveAndLeaveMatchContract() throws Exception {
        MvcResult created = mvc.perform(post("/groups")
                .requestAttr(SessionFilter.USER_ID, ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"nhom\"}"))
            .andReturn();
        String groupId = JsonPath.read(created.getResponse().getContentAsString(), "$.groupId");

        mvc.perform(post("/groups/" + groupId + "/members")
                .requestAttr(SessionFilter.USER_ID, ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + memberId + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("MEMBER"));

        mvc.perform(post("/groups/" + groupId + "/members")
                .requestAttr(SessionFilter.USER_ID, ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + memberId + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CONFLICT"));

        mvc.perform(delete("/groups/" + groupId + "/members/" + memberId)
                .requestAttr(SessionFilter.USER_ID, memberId))
            .andExpect(status().isNoContent());

        mvc.perform(get("/groups").requestAttr(SessionFilter.USER_ID, ownerId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].role").value("OWNER"));
    }

    @Test
    void missingGroupIsNotFound() throws Exception {
        mvc.perform(post("/groups/" + UUID.randomUUID() + "/members")
                .requestAttr(SessionFilter.USER_ID, ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + memberId + "\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
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
}
