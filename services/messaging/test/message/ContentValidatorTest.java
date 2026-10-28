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
    void mediaNeedsUrlAndPositiveSizes() {
        assertNull(ContentValidator.problem(new MessageContent.Image("https://cdn/a.png", null, null, null, null)));
        assertNotNull(ContentValidator.problem(new MessageContent.Image(" ", null, null, null, null)));
        assertNotNull(ContentValidator.problem(new MessageContent.Video("https://cdn/a.mp4", null, 0, 10, null, null)));
    }

    @Test
    void documentNeedsNameTypeAndSizeWithinLimit() {
        assertNull(ContentValidator.problem(new MessageContent.Document("https://cdn/a.pdf", "a.pdf", "application/pdf", 10)));
        assertNotNull(ContentValidator.problem(new MessageContent.Document("https://cdn/a.pdf", "", "application/pdf", 10)));
        assertNotNull(ContentValidator.problem(new MessageContent.Document("https://cdn/a.pdf", "a.pdf", "application/pdf",
            ContentValidator.DOCUMENT_MAX_BYTES + 1)));
    }
}
