package app;

import identity.event.GroupMembershipChange;
import identity.event.GroupMembershipListener;
import service.SystemMessagePoster;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Glue between identity and messaging: when group membership changes, members get a SYSTEM message in the
 * group's conversation. The group's id is the conversation's id.
 */
@Component
@Profile("app")
public final class GroupSystemMessages implements GroupMembershipListener {
    private final SystemMessagePoster messages;
    private final UserNames names;

    public GroupSystemMessages(SystemMessagePoster messages, UserNames names) {
        this.messages = messages;
        this.names = names;
    }

    @Override
    public void onChange(GroupMembershipChange change) {
        String actor = names.displayNameOf(change.actorId());
        String target = change.targetId() == null ? null : names.displayNameOf(change.targetId());
        String text = GroupSystemText.of(change, actor, target);
        if (text != null) {
            messages.postSystem(change.groupId(), text);
        }
    }
}
