package app;

/**
 * Names to show in notices written for members, for example "Alice added Bob".
 */
public interface UserNames {
    /**
     * The name to show for the user. Never null: an unknown user gets a neutral word.
     */
    String displayNameOf(String userId);
}
