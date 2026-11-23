package identity.auth;

import java.util.List;

public final class SessionList {
    private final List<SessionInfo> sessions;

    public SessionList(List<SessionInfo> sessions) {
        this.sessions = List.copyOf(sessions);
    }

    public List<SessionInfo> getSessions() {
        return sessions;
    }
}
