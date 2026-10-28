package conversation;

/**
 * Where one user is in one conversation. unreadCount counts messages from other people after lastReadSeq,
 * deleted ones left out. lastReadSeq is 0 before the user has read anything.
 */
public record ReadState(long lastReadSeq, long unreadCount) {
}
