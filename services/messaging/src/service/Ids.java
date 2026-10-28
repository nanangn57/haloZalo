package service;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ids are UUIDs in one spelling: lowercase. Postgres returns UUIDs lowercase, so an id sent in uppercase must
 * compare equal to what was stored, and come back the same way every time.
 */
public final class Ids {
    private static final Pattern UUID_PATTERN =
        Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private Ids() {
    }

    /**
     * Returns the lowercase UUID, or null when the value is not a UUID.
     */
    public static String canonical(String value) {
        if (value == null || !UUID_PATTERN.matcher(value).matches()) {
            return null;
        }
        return UUID.fromString(value).toString();
    }
}
