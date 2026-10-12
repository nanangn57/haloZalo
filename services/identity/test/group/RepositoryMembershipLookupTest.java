package group;

import storage.MembershipRepository;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryMembershipLookupTest {
    @Test
    void memberAndOwnerBothCount() {
        MemoryMemberships memberships = new MemoryMemberships();
        memberships.save(new Membership("group-1", "owner", Instant.now(), Role.OWNER));
        memberships.save(new Membership("group-1", "member", Instant.now(), Role.MEMBER));
        RepositoryMembershipLookup lookup = new RepositoryMembershipLookup(memberships);

        assertTrue(lookup.isMember("group-1", "owner"));
        assertTrue(lookup.isMember("group-1", "member"));
    }

    @Test
    void missingRowIsNotAMember() {
        RepositoryMembershipLookup lookup = new RepositoryMembershipLookup(new MemoryMemberships());

        assertFalse(lookup.isMember("group-1", "stranger"));
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
}
