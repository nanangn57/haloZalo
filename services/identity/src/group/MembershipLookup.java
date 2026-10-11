package group;

public interface MembershipLookup {
    boolean isMember(String groupId, String userId);
}
