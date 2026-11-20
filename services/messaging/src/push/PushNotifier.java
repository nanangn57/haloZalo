package push;

import java.util.Collection;

/**
 * Sends a short offline notification for a new message.
 */
public interface PushNotifier {
    void notifyNewMessage(Collection<String> offlineUserIds, String conversationId, String preview);
}
