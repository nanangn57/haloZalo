package identity.storage;

import identity.session.Session;

public interface SessionRepository {
    void save(Session session);

    Session findById(String sessionId);
}
