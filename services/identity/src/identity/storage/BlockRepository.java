package identity.storage;

import java.util.List;

public interface BlockRepository {
    void save(String blockerId, String blockedId);

    void delete(String blockerId, String blockedId);

    boolean exists(String blockerId, String blockedId);

    List<String> blockedIds(String blockerId);
}
