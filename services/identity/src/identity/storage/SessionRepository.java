package identity.storage;

import identity.session.Session;

import java.time.Instant;
import java.util.List;

public interface SessionRepository {
    void save(Session session);

    Session findById(String sessionId);

    Session findByUserIdAndDeviceId(String userId, String deviceId);

    List<Session> findByUserId(String userId);

    void delete(String sessionId);

    void touch(String sessionId, Instant lastSeenAt, Instant expiresAt);
}
