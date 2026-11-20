package media;

import message.MessageType;

import java.util.Locale;
import java.util.Map;

/**
 * Which files are accepted, and as which kind. Anything not listed is rejected: in particular SVG and HTML, which a
 * browser would run as script when opened from the API origin.
 */
public final class MediaTypes {
    private static final Map<String, MessageType> KINDS = Map.ofEntries(
        Map.entry("image/jpeg", MessageType.IMAGE),
        Map.entry("image/png", MessageType.IMAGE),
        Map.entry("image/gif", MessageType.IMAGE),
        Map.entry("image/webp", MessageType.IMAGE),
        Map.entry("video/mp4", MessageType.VIDEO),
        Map.entry("video/webm", MessageType.VIDEO),
        Map.entry("video/quicktime", MessageType.VIDEO),
        Map.entry("application/pdf", MessageType.DOCUMENT),
        Map.entry("text/plain", MessageType.DOCUMENT),
        Map.entry("text/csv", MessageType.DOCUMENT),
        Map.entry("application/zip", MessageType.DOCUMENT),
        Map.entry("application/msword", MessageType.DOCUMENT),
        Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", MessageType.DOCUMENT),
        Map.entry("application/vnd.ms-excel", MessageType.DOCUMENT),
        Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", MessageType.DOCUMENT),
        Map.entry("application/vnd.ms-powerpoint", MessageType.DOCUMENT),
        Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", MessageType.DOCUMENT));

    /**
     * Browsers send application/octet-stream, or nothing, for types the OS does not know. Then the extension decides.
     */
    private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
        Map.entry("jpg", "image/jpeg"),
        Map.entry("jpeg", "image/jpeg"),
        Map.entry("png", "image/png"),
        Map.entry("gif", "image/gif"),
        Map.entry("webp", "image/webp"),
        Map.entry("mp4", "video/mp4"),
        Map.entry("webm", "video/webm"),
        Map.entry("mov", "video/quicktime"),
        Map.entry("pdf", "application/pdf"),
        Map.entry("txt", "text/plain"),
        Map.entry("csv", "text/csv"),
        Map.entry("zip", "application/zip"),
        Map.entry("doc", "application/msword"),
        Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        Map.entry("xls", "application/vnd.ms-excel"),
        Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        Map.entry("ppt", "application/vnd.ms-powerpoint"),
        Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"));

    private MediaTypes() {
    }

    /**
     * The accepted MIME type for the upload, or null when the file type is not accepted.
     */
    public static String mimeType(String declared, String fileName) {
        String mime = declared == null ? "" : declared.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (mime.isEmpty() || mime.equals("application/octet-stream")) {
            int dot = fileName.lastIndexOf('.');
            mime = dot < 0 ? "" : BY_EXTENSION.getOrDefault(fileName.substring(dot + 1).toLowerCase(Locale.ROOT), "");
        }
        return KINDS.containsKey(mime) ? mime : null;
    }

    public static MessageType kind(String mimeType) {
        return KINDS.get(mimeType);
    }

    /**
     * Whether the first bytes of an image are what its MIME type says. Other kinds are not checked: they are served
     * with nosniff, and documents always as a download, so a mislabelled file is never run by the browser.
     */
    public static boolean looksLike(String mimeType, byte[] head) {
        return switch (mimeType) {
            case "image/jpeg" -> startsWith(head, 0, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(head, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/gif" -> startsWith(head, 0, 'G', 'I', 'F', '8');
            case "image/webp" -> startsWith(head, 0, 'R', 'I', 'F', 'F') && startsWith(head, 8, 'W', 'E', 'B', 'P');
            default -> true;
        };
    }

    /**
     * Keeps only the last path segment, drops control characters, and caps the length, so the name is safe to show
     * and to put in Content-Disposition.
     */
    public static String safeFileName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.codePoints()
            .filter(c -> !Character.isISOControl(c))
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString()
            .trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "file";
        }
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private static boolean startsWith(byte[] head, int offset, int... expected) {
        if (head.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
