package analytics.query;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class Dashboard {
    private final List<DayTotal> messagesByDay;
    private final long activeUsers;
    private final List<GroupTotal> topGroups;
    private final long dau;
    private final long wau;
    private final long mau;
    private final List<DayTotal> newUsersByDay;
    private final VerifyFunnel verifyFunnel;
    private final double messagesPerActiveUser;
    private final double activeMessengersRate;
    private final long friendRequests;
    private final double friendAcceptRate;
    private final long groupsCreated;
    private final double avgMembersPerGroup;
    private final List<MediaDayTotal> mediaByDay;
    private final String from;
    private final String to;

    public Dashboard(
        List<DayTotal> messagesByDay,
        long activeUsers,
        List<GroupTotal> topGroups,
        long dau,
        long wau,
        long mau,
        List<DayTotal> newUsersByDay,
        VerifyFunnel verifyFunnel,
        double messagesPerActiveUser,
        double activeMessengersRate,
        long friendRequests,
        double friendAcceptRate,
        long groupsCreated,
        double avgMembersPerGroup,
        List<MediaDayTotal> mediaByDay,
        DateRange range
    ) {
        this.messagesByDay = List.copyOf(messagesByDay);
        this.activeUsers = activeUsers;
        this.topGroups = List.copyOf(topGroups);
        this.dau = dau;
        this.wau = wau;
        this.mau = mau;
        this.newUsersByDay = List.copyOf(newUsersByDay);
        this.verifyFunnel = verifyFunnel;
        this.messagesPerActiveUser = messagesPerActiveUser;
        this.activeMessengersRate = activeMessengersRate;
        this.friendRequests = friendRequests;
        this.friendAcceptRate = friendAcceptRate;
        this.groupsCreated = groupsCreated;
        this.avgMembersPerGroup = avgMembersPerGroup;
        this.mediaByDay = List.copyOf(mediaByDay);
        this.from = range.from() == null ? null : range.from().toString();
        this.to = range.to() == null ? null : range.to().toString();
    }

    public List<DayTotal> getMessagesByDay() {
        return messagesByDay;
    }

    public long getActiveUsers() {
        return activeUsers;
    }

    public List<GroupTotal> getTopGroups() {
        return topGroups;
    }

    public long getDau() {
        return dau;
    }

    public long getWau() {
        return wau;
    }

    public long getMau() {
        return mau;
    }

    public List<DayTotal> getNewUsersByDay() {
        return newUsersByDay;
    }

    public VerifyFunnel getVerifyFunnel() {
        return verifyFunnel;
    }

    public double getMessagesPerActiveUser() {
        return messagesPerActiveUser;
    }

    public double getActiveMessengersRate() {
        return activeMessengersRate;
    }

    public long getFriendRequests() {
        return friendRequests;
    }

    public double getFriendAcceptRate() {
        return friendAcceptRate;
    }

    public long getGroupsCreated() {
        return groupsCreated;
    }

    public double getAvgMembersPerGroup() {
        return avgMembersPerGroup;
    }

    public List<MediaDayTotal> getMediaByDay() {
        return mediaByDay;
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
