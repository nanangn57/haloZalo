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
 * Media upload/download. Returns a URL the client puts into an IMAGE/DOCUMENT/VIDEO message body.
 */
@RestController
public final class MediaController {
    private static final long IMAGE_MAX = 5L * 1024 * 1024;
    private static final long DOCUMENT_MAX = 10L * 1024 * 1024;
    private static final long VIDEO_MAX = 20L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/webm");

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
            default -> throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION, "type must be IMAGE, DOCUMENT, or VIDEO");
        };
        if (file.getSize() > max) {
            throw new MessageService.Rejected(
                MessageService.Rejected.Reason.VALIDATION,
                mediaType + " exceeds " + max + " bytes"
            );
        }
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if ("IMAGE".equals(mediaType) && !IMAGE_TYPES.contains(contentType)) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION, "Unsupported image content type");
        }
        if ("VIDEO".equals(mediaType) && !VIDEO_TYPES.contains(contentType)) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION, "Unsupported video content type");
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

    private static String normalizeType(String type) {
        return type == null ? "" : type.trim().toUpperCase();
    }
}
