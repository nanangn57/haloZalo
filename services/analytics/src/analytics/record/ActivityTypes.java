package analytics.record;

public final class ActivityTypes {
    public static final String REGISTERED = "identity.user.registered";
    public static final String VERIFIED = "identity.user.verified";
    public static final String LOGGED_IN = "identity.user.logged_in";
    public static final String GROUP_CREATED = "identity.group.created";
    public static final String FRIEND_REQUEST_SENT = "identity.friend.request_sent";
    public static final String FRIEND_REQUEST_ACCEPTED = "identity.friend.request_accepted";
    public static final String MESSAGE_CREATED = "messaging.message.created";
    public static final String MEDIA_UPLOADED = "messaging.media.uploaded";

    private ActivityTypes() {
    }

    public static boolean isTracked(String type) {
        return REGISTERED.equals(type)
            || VERIFIED.equals(type)
            || LOGGED_IN.equals(type)
            || GROUP_CREATED.equals(type)
            || FRIEND_REQUEST_SENT.equals(type)
            || FRIEND_REQUEST_ACCEPTED.equals(type)
            || MESSAGE_CREATED.equals(type)
            || MEDIA_UPLOADED.equals(type);
    }

    public static boolean isMediaMessage(String contentType) {
        return "IMAGE".equals(contentType)
            || "DOCUMENT".equals(contentType)
            || "VIDEO".equals(contentType)
            || "AUDIO".equals(contentType);
    }

    public static boolean isTextLike(String contentType) {
        return "TEXT".equals(contentType) || "EMOTION".equals(contentType) || "SYSTEM".equals(contentType);
    }
}
