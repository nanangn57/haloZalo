package media;

import event.EnvelopeEventPublisher;
import http.UserId;
import service.MessageService;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Media upload/download. Returns a URL the client puts into an IMAGE/DOCUMENT/VIDEO/AUDIO message body.
 */
@RestController
public final class MediaController {
    private static final long IMAGE_MAX = 5L * 1024 * 1024;
    private static final long DOCUMENT_MAX = 10L * 1024 * 1024;
    private static final long VIDEO_MAX = 20L * 1024 * 1024;
    private static final long AUDIO_MAX = 5L * 1024 * 1024;
    private static final Set<String> AUDIO_TYPES = Set.of(
        "audio/mpeg", "audio/mp3", "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac", "audio/ogg",
        "audio/webm", "audio/wav", "audio/x-wav", "audio/3gpp", "audio/amr");
    private static final Set<String> IMAGE_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp", "image/heic", "image/heif");
    // Phone galleries: quicktime/mov, 3gp, mpeg, m4v — not only mp4/webm.
    private static final Set<String> VIDEO_TYPES = Set.of(
        "video/mp4", "video/webm", "video/quicktime", "video/x-m4v",
        "video/3gpp", "video/3gpp2", "video/mpeg", "video/x-msvideo", "application/mp4");

    private final MediaStore store;
    private final EnvelopeEventPublisher events;

    public MediaController(MediaStore store, EnvelopeEventPublisher events) {
        this.store = store;
        this.events = events;
    }

    @PostMapping(path = "/media/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(
        @UserId String userId,
        @RequestParam("file") MultipartFile file,
        @RequestParam("type") String type,
        @RequestParam(value = "conversationId", required = false) String conversationId
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION, "file is required");
        }
        String mediaType = normalizeType(type);
        long max = switch (mediaType) {
            case "IMAGE" -> IMAGE_MAX;
            case "DOCUMENT" -> DOCUMENT_MAX;
            case "VIDEO" -> VIDEO_MAX;
            case "AUDIO" -> AUDIO_MAX;
            default -> throw new MessageService.Rejected(
                MessageService.Rejected.Reason.VALIDATION, "type must be IMAGE, DOCUMENT, VIDEO, or AUDIO");
        };
        if (file.getSize() > max) {
            throw new MessageService.Rejected(
                MessageService.Rejected.Reason.VALIDATION,
                mediaType + " exceeds " + max + " bytes"
            );
        }
        String contentType = resolveContentType(file.getContentType(), file.getOriginalFilename(), mediaType);
        if ("IMAGE".equals(mediaType) && !IMAGE_TYPES.contains(baseType(contentType))) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION, "Unsupported image content type");
        }
        if ("VIDEO".equals(mediaType) && !VIDEO_TYPES.contains(baseType(contentType))) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION, "Unsupported video content type");
        }
        if ("AUDIO".equals(mediaType) && !AUDIO_TYPES.contains(baseType(contentType))) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION, "Unsupported audio content type");
        }
        MediaStore.Stored stored = store.store(file.getBytes(), contentType, file.getOriginalFilename());
        events.publishMediaUploaded(userId, conversationId, mediaType);
        return Map.of(
            "mediaId", stored.id(),
            "url", stored.url(),
            "type", mediaType,
            "contentType", contentType,
            "fileName", stored.fileName(),
            "size", stored.size()
        );
    }

    @GetMapping("/media/{mediaId}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String mediaId) {
        MediaStore.Opened opened;
        try {
            opened = store.open(mediaId);
        } catch (IOException ex) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + opened.fileName() + "\"");
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
            .headers(headers)
            .contentType(MediaType.parseMediaType(opened.contentType()));
        if (opened.size() >= 0) {
            builder.contentLength(opened.size());
        }
        return builder.body(new InputStreamResource(opened.stream()));
    }

    /**
     * "audio/webm;codecs=opus" is audio/webm: browsers add codec parameters to recorded voice notes.
     */
    static String baseType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        int parameters = contentType.indexOf(';');
        return (parameters < 0 ? contentType : contentType.substring(0, parameters)).trim().toLowerCase();
    }

    /**
     * Expo/iOS often uploads with empty or application/octet-stream Content-Type; recover from the filename.
     */
    static String resolveContentType(String declared, String originalName, String mediaType) {
        String base = baseType(declared);
        if (!base.isEmpty() && !"application/octet-stream".equals(base)) {
            return base;
        }
        String guessed = guessFromFileName(originalName);
        if (guessed != null) {
            return guessed;
        }
        return switch (mediaType) {
            case "IMAGE" -> "image/jpeg";
            case "VIDEO" -> "video/mp4";
            case "AUDIO" -> "audio/mp4";
            default -> base.isEmpty() ? "application/octet-stream" : base;
        };
    }

    static String guessFromFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return null;
        }
        String name = originalName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return switch (name.substring(dot + 1).toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "heic" -> "image/heic";
            case "heif" -> "image/heif";
            case "mp4", "m4v" -> "video/mp4";
            case "webm" -> "video/webm";
            case "mov" -> "video/quicktime";
            case "3gp", "3gpp" -> "video/3gpp";
            case "avi" -> "video/x-msvideo";
            case "mpeg", "mpg" -> "video/mpeg";
            case "m4a" -> "audio/mp4";
            case "mp3" -> "audio/mpeg";
            case "aac" -> "audio/aac";
            case "wav" -> "audio/wav";
            case "ogg" -> "audio/ogg";
            case "amr" -> "audio/amr";
            case "pdf" -> "application/pdf";
            default -> null;
        };
    }

    private static String normalizeType(String type) {
        return type == null ? "" : type.trim().toUpperCase();
    }
}
