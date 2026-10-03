package storage;

import conversation.Conversation;
import conversation.ConversationType;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

@Component
public final class MongoConversationRepository implements ConversationRepository {
    private final MongoCollection<Document> conversations;

    public MongoConversationRepository(MongoTemplate mongo) {
        this.conversations = mongo.getCollection("conversations");
    }

    @Override
    public boolean insert(Conversation conversation) {
        Document row = new Document("_id", conversation.getConversationId())
            .append("type", conversation.getType().name())
            .append("memberIds", List.copyOf(conversation.getMemberIds()))
            .append("lastSeq", conversation.getLastSeq())
            .append("createdAt", Date.from(conversation.getCreatedAt()));
        try {
            conversations.insertOne(row);
            return true;
        } catch (MongoWriteException ex) {
            if (ex.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                return false;
            }
            throw ex;
        }
    }

    @Override
    public Conversation findById(String conversationId) {
        Document row = conversations.find(eq("_id", conversationId)).first();
        if (row == null) {
            return null;
        }
        return new Conversation(
            row.getString("_id"),
            ConversationType.valueOf(row.getString("type")),
            new HashSet<>(row.getList("memberIds", String.class)),
            row.get("lastSeq", Number.class).longValue(),
            row.getDate("createdAt").toInstant()
        );
    }

    @Override
    public long nextSeq(String conversationId) {
        Document row = conversations.findOneAndUpdate(
            eq("_id", conversationId),
            Updates.inc("lastSeq", 1L),
            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        );
        if (row == null) {
            throw new IllegalStateException("Conversation disappeared: " + conversationId);
        }
        return row.get("lastSeq", Number.class).longValue();
    }
}
