package identity.account;

import com.fasterxml.jackson.databind.JsonNode;

public final class ProfilePatch {
    private final boolean displayNameSet;
    private final String displayName;
    private final boolean avatarSet;
    private final String avatar;
    private final boolean bioSet;
    private final String bio;

    private ProfilePatch(
        boolean displayNameSet,
        String displayName,
        boolean avatarSet,
        String avatar,
        boolean bioSet,
        String bio
    ) {
        this.displayNameSet = displayNameSet;
        this.displayName = displayName;
        this.avatarSet = avatarSet;
        this.avatar = avatar;
        this.bioSet = bioSet;
        this.bio = bio;
    }

    public static ProfilePatch from(JsonNode body) {
        if (body == null || !body.isObject()) {
            return new ProfilePatch(false, null, false, null, false, null);
        }
        return new ProfilePatch(
            body.has("displayName"),
            text(body, "displayName"),
            body.has("avatar"),
            text(body, "avatar"),
            body.has("bio"),
            text(body, "bio")
        );
    }

    private static String text(JsonNode body, String field) {
        return body.has(field) && !body.get(field).isNull() ? body.get(field).asText() : null;
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

    public boolean hasBio() {
        return bioSet;
    }

    public String getBio() {
        return bio;
    }
}
