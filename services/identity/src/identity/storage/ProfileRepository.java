package identity.storage;

import identity.account.Profile;

public interface ProfileRepository {
    Profile find(String userId);

    void save(Profile profile);
}
