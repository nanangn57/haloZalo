package storage;

import message.Message;
import message.MessageContent;
import message.MessageMetadata;
import message.MessageStatus;
import message.MessageType;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@Component
public final class MongoMessageRepository implements MessageRepository {
    private final MongoCollection<Document> messages;

    public MongoMessageRepository(MongoTemplate mongo) {
        this.messages = mongo.getCollection("messages");
        messages.createIndex(Indexes.ascending("senderId", "clientMsgId"), new IndexOptions().unique(true));
        messages.createIndex(Indexes.ascending("conversationId", "seq"), new IndexOptions().unique(true));
    }

    @Override
    public boolean insert(Message message) {
        try {
            messages.insertOne(toDocument(message));
            return true;
        } catch (MongoWriteException ex) {
            if (ex.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                return false;
            }
            throw ex;
        }
    }

    @Override
    public void update(Message message) {
        messages.replaceOne(eq("_id", message.getMessageId()), toDocument(message));
    }

    @Override
    public Message findById(String messageId) {
        return fromDocument(messages.find(eq("_id", messageId)).first());
    }

    @Override
    public Message findBySenderAndClientMsgId(String senderId, String clientMsgId) {
        return fromDocument(messages.find(and(eq("senderId", senderId), eq("clientMsgId", clientMsgId))).first());
    }

    static Document toDocument(Message message) {
        return new Document("_id", message.getMessageId())
            .append("conversationId", message.getConversationId())
            .append("seq", message.getSeq())
            .append("senderId", message.getSenderId())
            .append("clientMsgId", message.getClientMsgId())
            .append("type", message.getType().name())
            .append("content", contentDocument(message.getContent()))
            .append("metadata", message.getMetadata() == null ? null : new Document(new LinkedHashMap<>(message.getMetadata().getValues())))
            .append("replyTo", message.getReplyTo())
            .append("forwardedFrom", message.getForwardedFrom())
            .append("status", message.getStatus().name())
            .append("createdAt", date(message.getCreatedAt()))
            .append("updatedAt", date(message.getUpdatedAt()))
            .append("deletedAt", date(message.getDeletedAt()));
    }

    static Message fromDocument(Document row) {
        if (row == null) {
            return null;
        }
        Document metadata = row.get("metadata", Document.class);
        return new Message(
            row.getString("_id"),
            row.getString("conversationId"),
            row.get("seq", Number.class).longValue(),
            row.getString("senderId"),
            row.getString("clientMsgId"),
            content(MessageType.valueOf(row.getString("type")), row.get("content", Document.class)),
            metadata == null ? null : new MessageMetadata(stringMap(metadata)),
            row.getString("replyTo"),
            row.getString("forwardedFrom"),
            MessageStatus.valueOf(row.getString("status")),
            instant(row.getDate("createdAt")),
            instant(row.getDate("updatedAt")),
            instant(row.getDate("deletedAt"))
        );
    }

    private static Document contentDocument(MessageContent content) {
        if (content instanceof MessageContent.Text text) {
            return new Document("text", text.text());
        }
        if (content instanceof MessageContent.Emotion emotion) {
            return new Document("code", emotion.code());
        }
        if (content instanceof MessageContent.Image image) {
            return new Document("url", image.url())
                .append("thumbnailUrl", image.thumbnailUrl())
                .append("width", image.width())
                .append("height", image.height())
                .append("caption", image.caption());
        }
        if (content instanceof MessageContent.Video video) {
            return new Document("url", video.url())
                .append("thumbnailUrl", video.thumbnailUrl())
                .append("width", video.width())
                .append("height", video.height())
                .append("duration", video.duration())
                .append("caption", video.caption());
        }
        MessageContent.Document document = (MessageContent.Document) content;
        return new Document("url", document.url())
            .append("fileName", document.fileName())
            .append("mimeType", document.mimeType())
            .append("size", document.size());
    }

    private static MessageContent content(MessageType type, Document row) {
        return switch (type) {
            case TEXT -> new MessageContent.Text(row.getString("text"));
            case EMOTION -> new MessageContent.Emotion(row.getString("code"));
            case IMAGE -> new MessageContent.Image(
                row.getString("url"), row.getString("thumbnailUrl"),
                row.getInteger("width"), row.getInteger("height"), row.getString("caption"));
            case VIDEO -> new MessageContent.Video(
                row.getString("url"), row.getString("thumbnailUrl"),
                row.getInteger("width"), row.getInteger("height"), row.getInteger("duration"), row.getString("caption"));
            case DOCUMENT -> new MessageContent.Document(
                row.getString("url"), row.getString("fileName"), row.getString("mimeType"),
                row.get("size", Number.class).longValue());
        };
    }

    private static Map<String, String> stringMap(Document row) {
        Map<String, String> values = new LinkedHashMap<>();
        row.forEach((key, value) -> values.put(key, String.valueOf(value)));
        return values;
    }

    private static Date date(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

    private static Instant instant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
