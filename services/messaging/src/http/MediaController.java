package http;

import api.MediaResponse;
import media.Media;
import media.MediaLinks;
import message.MessageType;
import service.MediaService;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@RestController
public final class MediaController {
    private final MediaService media;
    private final MediaLinks links;

    public MediaController(MediaService media, MediaLinks links) {
        this.media = media;
        this.links = links;
    }

    /**
     * Step one of sending a file: store it, then send a message of the returned kind with the returned mediaId.
     */
    @PostMapping(path = "/conversations/{conversationId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaResponse> upload(
        @UserId String userId,
        @PathVariable String conversationId,
        @RequestPart("file") MultipartFile file) throws IOException {
        try (InputStream content = file.getInputStream()) {
            Media stored = media.upload(conversationId, userId, file.getOriginalFilename(), file.getContentType(), content);
            return ResponseEntity.status(201).body(new MediaResponse(stored, links));
        }
    }

    /**
     * The signed link is the permission, so there is no user here: a browser loads it from an img or video tag.
     * Range requests work, so videos can seek. Images and videos open inline; documents always download.
     */
    @GetMapping("/media/{mediaId}")
    public ResponseEntity<Resource> download(
        @PathVariable String mediaId,
        @RequestParam(required = false) Long expires,
        @RequestParam(required = false) String sig) {
        MediaService.Download download = media.open(mediaId, expires, sig);
        Media file = download.media();
        ContentDisposition disposition = (file.kind() == MessageType.DOCUMENT
            ? ContentDisposition.attachment()
            : ContentDisposition.inline())
            .filename(file.fileName(), StandardCharsets.UTF_8)
            .build();
        long remaining = Math.max(0, download.expires() - Instant.now().getEpochSecond());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.mimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header("X-Content-Type-Options", "nosniff")
            // A file opened directly from the API origin can never run script or load anything.
            .header("Content-Security-Policy", "default-src 'none'; sandbox")
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(remaining)).cachePrivate())
            .body(download.bytes());
    }
}
