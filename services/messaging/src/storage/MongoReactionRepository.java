package storage;

import reaction.MessageReaction;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@Component
public final class MongoReactionRepository implements ReactionRepository {
    private final MongoCollection<Document> reactions;

    public MongoReactionRepository(MongoTemplate mongo) {
        this.reactions = mongo.getCollection("message_reactions");
        reactions.createIndex(Indexes.ascending("messageId", "userId", "code"), new IndexOptions().unique(true));
    }

    @Override
    public boolean insert(MessageReaction reaction) {
        Document row = new Document("_id", reaction.getReactionId())
            .append("messageId", reaction.getMessageId())
            .append("userId", reaction.getUserId())
            .append("code", reaction.getCode())
            .append("createdAt", Date.from(reaction.getCreatedAt()));
        try {
            reactions.insertOne(row);
            return true;
        } catch (MongoWriteException ex) {
            if (ex.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                return false;
            }
            throw ex;
        }
    }

    @Override
    public boolean delete(String messageId, String userId, String code) {
        return reactions.deleteOne(and(eq("messageId", messageId), eq("userId", userId), eq("code", code)))
            .getDeletedCount() > 0;
    }

    @Override
    public List<MessageReaction> findByMessageId(String messageId) {
        List<MessageReaction> rows = new ArrayList<>();
        for (Document row : reactions.find(eq("messageId", messageId))) {
            rows.add(new MessageReaction(
                row.getString("_id"),
                row.getString("messageId"),
                row.getString("userId"),
                row.getString("code"),
                row.getDate("createdAt").toInstant()
            ));
        }
        return rows;
    }
}
