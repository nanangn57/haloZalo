package api;

import reaction.MessageReaction;

import java.util.List;

/**
 * Body of GET .../messages/{messageId}/reactions. One entry per user and code, oldest first.
 */
public final class ReactionsResponse {
    private final List<Reaction> reactions;

    public ReactionsResponse(List<MessageReaction> reactions) {
        this.reactions = reactions.stream()
            .sorted((left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt()))
            .map(Reaction::new)
            .toList();
    }

    public List<Reaction> getReactions() {
        return reactions;
    }

    public static final class Reaction {
        private final String userId;
        private final String code;
        private final String createdAt;

        public Reaction(MessageReaction reaction) {
            this.userId = reaction.getUserId();
            this.code = reaction.getCode();
            this.createdAt = reaction.getCreatedAt().toString();
        }

        public String getUserId() {
            return userId;
        }

        public String getCode() {
            return code;
        }

        public String getCreatedAt() {
            return createdAt;
        }
    }
}
