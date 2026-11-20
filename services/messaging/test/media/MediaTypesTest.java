package media;

import message.MessageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaTypesTest {
    @Test
    void declaredTypeWinsAndParametersAreDropped() {
        assertEquals("image/png", MediaTypes.mimeType("IMAGE/PNG", "x.jpg"));
        assertEquals("text/plain", MediaTypes.mimeType("text/plain; charset=utf-8", "notes"));
        assertEquals(MessageType.VIDEO, MediaTypes.kind("video/quicktime"));
    }

    @Test
    void extensionDecidesOnlyWhenTheBrowserDidNotKnow() {
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            MediaTypes.mimeType("application/octet-stream", "Report.DOCX"));
        assertEquals("video/quicktime", MediaTypes.mimeType(null, "clip.mov"));
        assertNull(MediaTypes.mimeType("application/octet-stream", "tool.exe"));
    }

    @Test
    void scriptableTypesAreNeverAccepted() {
        assertNull(MediaTypes.mimeType("image/svg+xml", "a.svg"));
        assertNull(MediaTypes.mimeType("text/html", "a.html"));
        assertNull(MediaTypes.mimeType("application/octet-stream", "a.svg"));
    }

    @Test
    void imageHeadersMustMatchTheirType() {
        assertTrue(MediaTypes.looksLike("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        assertTrue(MediaTypes.looksLike("image/gif", "GIF89a".getBytes()));
        assertTrue(MediaTypes.looksLike("image/webp", "RIFF0000WEBPVP8 ".getBytes()));
        assertFalse(MediaTypes.looksLike("image/webp", "RIFF0000WAVEfmt ".getBytes()));
        assertFalse(MediaTypes.looksLike("image/png", "<svg".getBytes()));
        assertFalse(MediaTypes.looksLike("image/jpeg", new byte[0]));
    }

    @Test
    void fileNamesKeepOnlyTheLastSegmentWithoutControlCharacters() {
        assertEquals("a.pdf", MediaTypes.safeFileName("../../etc/a.pdf"));
        assertEquals("b.pdf", MediaTypes.safeFileName("C:\\x\\b.pdf"));
        assertEquals("evil.pdf", MediaTypes.safeFileName("evil\r\n.pdf"));
        assertEquals("file", MediaTypes.safeFileName(".."));
        assertEquals("file", MediaTypes.safeFileName(null));
        assertEquals(200, MediaTypes.safeFileName("a".repeat(300)).length());
    }
}
