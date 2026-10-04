package message;

/**
 * Lifecycle of the message itself. Read state is not here: it is a marker per user and conversation.
 */
public enum MessageStatus {
    SENT,
    DELETED,
    ERROR
}
