package storage;

import verification.EmailVerification;

public interface VerificationRepository {
    void save(EmailVerification verification);

    EmailVerification findByToken(String token);

    EmailVerification findByUsername(String username);

    EmailVerification findByEmail(String email);

    void delete(String token);
}
