package analytics.access;

/**
 * Whether the caller may read platform-wide stats. Wired from identity platform role in the app process.
 */
public interface PlatformAdminGate {
    boolean isAdmin(String userId);
}
