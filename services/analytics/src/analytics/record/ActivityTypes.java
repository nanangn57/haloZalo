package analytics.record;

public final class ActivityTypes {
    public static final String LOGGED_IN = "identity.user.logged_in";
    public static final String GROUP_CREATED = "identity.group.created";
    public static final String MESSAGE_CREATED = "messaging.message.created";
    public static final String MEDIA_UPLOADED = "messaging.media.uploaded";

    private ActivityTypes() {
    }

    public static boolean isTracked(String type) {
        return LOGGED_IN.equals(type)
            || GROUP_CREATED.equals(type)
            || MESSAGE_CREATED.equals(type)
            || MEDIA_UPLOADED.equals(type);
    }
}
