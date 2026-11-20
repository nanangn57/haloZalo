package identity.access;

/**
 * In-process lookup so analytics can gate platform stats without reading identity tables.
 */
public interface PlatformRoleLookup {
    boolean isAdmin(String userId);
}
