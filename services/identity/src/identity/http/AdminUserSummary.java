package identity.http;

import analytics.query.UserActivity;
import identity.account.ProfileView;

public final class AdminUserSummary {
    private final ProfileView user;
    private final UserActivity activity;

    public AdminUserSummary(ProfileView user, UserActivity activity) {
        this.user = user;
        this.activity = activity;
    }

    public ProfileView getUser() {
        return user;
    }

    public UserActivity getActivity() {
        return activity;
    }
}
