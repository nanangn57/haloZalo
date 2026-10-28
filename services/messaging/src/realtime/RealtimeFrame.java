package realtime;

import java.util.List;

/**
 * One envelope to deliver, and the users whose open sockets should get it.
 */
public record RealtimeFrame(List<String> recipients, String envelope) {
}
