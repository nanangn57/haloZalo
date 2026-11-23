package push;

/**
 * One user's push settings. They apply to every conversation and every device of the user.
 * preview false means a push says only that a message arrived, not what it says.
 */
public record NotificationPrefs(boolean pushEnabled, boolean previewEnabled) {
    public static final NotificationPrefs DEFAULT = new NotificationPrefs(true, true);
}
