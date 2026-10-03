package storage;

import message.Message;
import message.MessageContent;
import message.MessageMetadata;
import message.MessageStatus;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MongoMessageRepositoryTest {
    private static final Instant CREATED = Instant.parse("2026-10-03T09:00:00.123Z");

    @Test
    void everyContentTypeSurvivesTheDocumentRoundTrip() {
        List<MessageContent> contents = List.of(
            new MessageContent.Text("hello \"world\""),
            new MessageContent.Emotion("love"),
            new MessageContent.Image("https://cdn/a.png", null, 640, 480, "cat"),
            new MessageContent.Video("https://cdn/a.mp4", "https://cdn/a.jpg", 1280, 720, 30, null),
            new MessageContent.Document("https://cdn/a.pdf", "a.pdf", "application/pdf", 2048L)
        );
        for (MessageContent content : contents) {
            Message read = MongoMessageRepository.fromDocument(MongoMessageRepository.toDocument(message(content, null)));

            assertEquals(content, read.getContent());
            assertEquals(content.type(), read.getType());
        }
    }

    @Test
    void coreFieldsAndMetadataSurviveTheDocumentRoundTrip() {
        Message original = message(new MessageContent.Text("hi"), new MessageMetadata(Map.of("clientVersion", "1.2")))
            .softDeleted(CREATED.plusSeconds(5));

        Document row = MongoMessageRepository.toDocument(original);
        Message read = MongoMessageRepository.fromDocument(row);

        assertEquals("TEXT", row.getString("type"));
        assertEquals(original.getMessageId(), row.getString("_id"));
        assertEquals(7L, read.getSeq());
        assertEquals("reply-1", read.getReplyTo());
        assertNull(read.getForwardedFrom());
        assertEquals(MessageStatus.DELETED, read.getStatus());
        assertEquals(CREATED, read.getCreatedAt());
        assertEquals(CREATED.plusSeconds(5), read.getDeletedAt());
        assertEquals(Map.of("clientVersion", "1.2"), read.getMetadata().getValues());
    }

    private static Message message(MessageContent content, MessageMetadata metadata) {
        return new Message("message-1", "conversation-1", 7, "sender-1", "client-1", content, metadata,
            "reply-1", null, MessageStatus.SENT, CREATED, CREATED, null);
    }
}
