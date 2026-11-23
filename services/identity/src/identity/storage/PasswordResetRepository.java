package identity.storage;

import identity.password.PasswordReset;

public interface PasswordResetRepository {
    void save(PasswordReset reset);

    PasswordReset findByToken(String token);

    void delete(String token);

    void deleteByUserId(String userId);
}
