package identity.account;

/**
 * Platform-wide account role. Distinct from group membership OWNER/ADMIN/MEMBER.
 */
public enum PlatformRole {
    USER,
    ADMIN;

    public static PlatformRole fromStorage(String value) {
        if (value == null || value.isBlank()) {
            return USER;
        }
        return PlatformRole.valueOf(value.trim().toUpperCase());
    }
}
