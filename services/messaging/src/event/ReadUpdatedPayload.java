package event;

/**
 * Payload of messaging.read.updated. Sent only to the reader's own devices.
 */
public record ReadUpdatedPayload(String conversationId, String userId, long lastReadSeq) {
}
