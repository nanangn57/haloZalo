package analytics.query;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class UserActivity {
    private final long logins;
    private final long messagesSent;
    private final long messagesReceived;
    private final long groupsCreated;
    private final long activeConversations;
    private final List<MessageDayTotal> messagesByDay;
    private final MediaTextSplit mediaVsText;
    private final DirectGroupSplit directVsGroup;
    private final List<HeatmapCell> heatmap;
    private final FriendActivity friends;
    private final List<ConversationTotal> topConversations;
    private final String from;
    private final String to;

    public UserActivity(
        long logins,
        long messagesSent,
        long messagesReceived,
        long groupsCreated,
        long activeConversations,
        List<MessageDayTotal> messagesByDay,
        MediaTextSplit mediaVsText,
        DirectGroupSplit directVsGroup,
        List<HeatmapCell> heatmap,
        FriendActivity friends,
        List<ConversationTotal> topConversations,
        DateRange range
    ) {
        this.logins = logins;
        this.messagesSent = messagesSent;
        this.messagesReceived = messagesReceived;
        this.groupsCreated = groupsCreated;
        this.activeConversations = activeConversations;
        this.messagesByDay = List.copyOf(messagesByDay);
        this.mediaVsText = mediaVsText;
        this.directVsGroup = directVsGroup;
        this.heatmap = List.copyOf(heatmap);
        this.friends = friends;
        this.topConversations = List.copyOf(topConversations);
        this.from = range.from() == null ? null : range.from().toString();
        this.to = range.to() == null ? null : range.to().toString();
    }

    public static UserActivity empty(DateRange range) {
        return new UserActivity(
            0,
            0,
            0,
            0,
            0,
            List.of(),
            new MediaTextSplit(0, 0),
            new DirectGroupSplit(0, 0),
            List.of(),
            new FriendActivity(0, 0, 0, 0),
            List.of(),
            range
        );
    }

    public long getLogins() {
        return logins;
    }

    public long getMessagesSent() {
        return messagesSent;
    }

    public long getMessagesReceived() {
        return messagesReceived;
    }

    public long getGroupsCreated() {
        return groupsCreated;
    }

    public long getActiveConversations() {
        return activeConversations;
    }

    public List<MessageDayTotal> getMessagesByDay() {
        return messagesByDay;
    }

    public MediaTextSplit getMediaVsText() {
        return mediaVsText;
    }

    public DirectGroupSplit getDirectVsGroup() {
        return directVsGroup;
    }

    public List<HeatmapCell> getHeatmap() {
        return heatmap;
    }

    public FriendActivity getFriends() {
        return friends;
    }

    public List<ConversationTotal> getTopConversations() {
        return topConversations;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getFrom() {
        return from;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getTo() {
        return to;
    }
}
