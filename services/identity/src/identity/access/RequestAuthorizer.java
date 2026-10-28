package identity.access;

public interface RequestAuthorizer {
    void authorize(String userId, String path);

    final class Forbidden extends RuntimeException {
        public Forbidden(String message) {
            super(message);
        }
    }
}
