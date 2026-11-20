package identity.account;

import com.fasterxml.jackson.databind.JsonNode;

public final class ProfilePatch {
    private final boolean displayNameSet;
    private final String displayName;
    private final boolean avatarSet;
    private final String avatar;

    private ProfilePatch(boolean displayNameSet, String displayName, boolean avatarSet, String avatar) {
        this.displayNameSet = displayNameSet;
        this.displayName = displayName;
        this.avatarSet = avatarSet;
        this.avatar = avatar;
    }

    public static ProfilePatch from(JsonNode body) {
        if (body == null || !body.isObject()) {
            return new ProfilePatch(false, null, false, null);
        }
        boolean name = body.has("displayName");
        boolean avatar = body.has("avatar");
        String displayName = name && !body.get("displayName").isNull() ? body.get("displayName").asText() : null;
        String picture = null;
        if (avatar && !body.get("avatar").isNull()) {
            picture = body.get("avatar").asText();
        }
        return new ProfilePatch(name, displayName, avatar, picture);
    }

    public boolean hasDisplayName() {
        return displayNameSet;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean hasAvatar() {
        return avatarSet;
    }

    public String getAvatar() {
        return avatar;
    }
}
