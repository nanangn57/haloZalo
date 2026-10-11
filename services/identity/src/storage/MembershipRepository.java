package storage;

import group.Membership;
import java.util.List;

public interface MembershipRepository {

    void save(Membership membership);

    void delete(String groupId, String userId);

    Membership find(String groupId, String userId);

    List<Membership> listByUser(String userId);
}
