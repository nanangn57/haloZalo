package identity.group;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Body of PATCH /groups/{groupId}. Keeps "field absent" apart from "field null" so a client can clear
 * the description or the avatar.
 */
public final class GroupPatch {
    private final boolean nameSet;
    private final String name;
    private final boolean descriptionSet;
    private final String description;
    private final boolean avatarSet;
    private final String avatar;

    private GroupPatch(
        boolean nameSet,
        String name,
        boolean descriptionSet,
        String description,
        boolean avatarSet,
        String avatar
    ) {
        this.nameSet = nameSet;
        this.name = name;
        this.descriptionSet = descriptionSet;
        this.description = description;
        this.avatarSet = avatarSet;
        this.avatar = avatar;
    }

    public static GroupPatch from(JsonNode body) {
        if (body == null || !body.isObject()) {
            return new GroupPatch(false, null, false, null, false, null);
        }
        return new GroupPatch(
            body.has("name"),
            text(body, "name"),
            body.has("description"),
            text(body, "description"),
            body.has("avatar"),
            text(body, "avatar")
        );
    }

    private static String text(JsonNode body, String field) {
        return body.has(field) && !body.get(field).isNull() ? body.get(field).asText() : null;
    }

    public boolean hasName() {
        return nameSet;
    }

    public String getName() {
        return name;
    }

    public boolean hasDescription() {
        return descriptionSet;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasAvatar() {
        return avatarSet;
    }

    public String getAvatar() {
        return avatar;
    }
}
