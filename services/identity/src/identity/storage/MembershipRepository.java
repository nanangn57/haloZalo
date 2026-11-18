package identity.storage;

import identity.group.Membership;
import identity.group.Role;
import java.util.List;

public interface MembershipRepository {

    void save(Membership membership);

    void delete(String groupId, String userId);

    Membership find(String groupId, String userId);

    List<Membership> listByUser(String userId);

    List<Membership> listByGroup(String groupId);

    void updateRole(String groupId, String userId, Role role);
}
