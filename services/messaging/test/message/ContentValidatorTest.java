package message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContentValidatorTest {
    @Test
    void textMustBeNonEmptyAndAtMost4096Utf8Bytes() {
        assertNull(ContentValidator.problem(new MessageContent.Text("a".repeat(4096))));
        assertNotNull(ContentValidator.problem(new MessageContent.Text("")));
        assertNotNull(ContentValidator.problem(new MessageContent.Text(null)));
        // 1366 three-byte characters are 4098 bytes, although only 1366 chars.
        assertEquals("Text body exceeds 4096 bytes", ContentValidator.problem(new MessageContent.Text("ạ".repeat(1366))));
    }

    @Test
    void emotionMustUseTheAgreedCodes() {
        assertNull(ContentValidator.problem(new MessageContent.Emotion("haha")));
        assertNotNull(ContentValidator.problem(new MessageContent.Emotion("heart")));
    }

    @Test
    void attachmentNeedsMediaIdNameTypeAndPositiveSize() {
        String id = "dddddddd-0000-4000-8000-000000000001";
        assertNull(ContentValidator.problem(new MessageContent.Image(id, "a.png", "image/png", 10, null, null, null)));
        assertNotNull(ContentValidator.problem(new MessageContent.Image(" ", "a.png", "image/png", 10, null, null, null)));
        assertNotNull(ContentValidator.problem(new MessageContent.Image(id, "a.png", "image/png", 10, 0, 10, null)));
        assertNull(ContentValidator.problem(new MessageContent.Video(id, "a.mp4", "video/mp4", 10, "clip")));
        assertNull(ContentValidator.problem(new MessageContent.Document(id, "a.pdf", "application/pdf", 10, null)));
        assertNotNull(ContentValidator.problem(new MessageContent.Document(id, "", "application/pdf", 10, null)));
        assertNotNull(ContentValidator.problem(new MessageContent.Document(id, "a.pdf", "application/pdf", 0, null)));
    }

    @Test
    void captionFollowsTheTextLimit() {
        String id = "dddddddd-0000-4000-8000-000000000001";
        assertNull(ContentValidator.problem(new MessageContent.Video(id, "a.mp4", "video/mp4", 10, "a".repeat(4096))));
        assertEquals("Caption exceeds 4096 bytes",
            ContentValidator.problem(new MessageContent.Video(id, "a.mp4", "video/mp4", 10, "a".repeat(4097))));
    }
}
