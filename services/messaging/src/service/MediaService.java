package service;

import event.EventPublisher;
import media.Media;
import media.MediaLinks;
import media.MediaProperties;
import media.MediaStore;
import media.MediaTypes;
import message.MessageContent;
import message.MessageType;
import storage.MediaRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.UUID;

/**
 * Upload is the first step of sending a file: the client uploads to the conversation, gets a mediaId, then sends a
 * message of the same kind with that mediaId. Uploading alone shows nothing to the other members.
 */
@Service
public final class MediaService {
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final int HEAD_BYTES = 16;

    private final MessageService messages;
    private final MediaRepository media;
    private final MediaStore store;
    private final MediaLinks links;
    private final MediaProperties limits;
    private final EventPublisher events;

    public MediaService(
        MessageService messages,
        MediaRepository media,
        MediaStore store,
        MediaLinks links,
        MediaProperties limits,
        EventPublisher events) {
        this.messages = messages;
        this.media = media;
        this.store = store;
        this.links = links;
        this.limits = limits;
        this.events = events;
    }

    /**
     * Stores a file for a member of the conversation. The kind comes from the MIME type, or from the file extension
     * when the browser sent none. Images must start with the bytes of their type.
     */
    public Media upload(String conversationId, String userId, String originalName, String declaredType, InputStream content) {
        MessageService.MemberOf member = messages.memberOf(conversationId, userId);
        String fileName = MediaTypes.safeFileName(originalName);
        String mimeType = MediaTypes.mimeType(declaredType, fileName);
        if (mimeType == null) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION, "File type is not supported");
        }
        MessageType kind = MediaTypes.kind(mimeType);
        long max = maxBytes(kind);
        String mediaId = UUID.randomUUID().toString();
        long size;
        try {
            size = store.put(mediaId, content, max);
        } catch (MediaStore.TooLarge tooLarge) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.TOO_LARGE,
                kind + " files can be at most " + max + " bytes");
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store upload", ex);
        }
        try {
            if (size == 0) {
                throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION, "File is empty");
            }
            Dimensions dimensions = kind == MessageType.IMAGE ? checkImage(mediaId, mimeType) : Dimensions.UNKNOWN;
            Media stored = new Media(mediaId, member.conversationId(), member.userId(), kind, fileName, mimeType, size,
                dimensions.width(), dimensions.height(), Instant.now().truncatedTo(ChronoUnit.MILLIS));
            media.insert(stored);
            events.publishMediaUploaded(stored);
            return stored;
        } catch (RuntimeException failure) {
            store.delete(mediaId);
            throw failure;
        }
    }

    /**
     * The content of a message that sends an uploaded file. Only the uploader can send it, only in the conversation
     * it was uploaded to, and only as the kind it was stored as. Forwarding copies the content, so a forwarded file
     * is not limited this way.
     */
    public MessageContent attachment(String conversationId, String senderId, MessageType type, String mediaId, String caption) {
        String id = Ids.canonical(mediaId);
        if (id == null) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION, "mediaId must be a UUID");
        }
        Media file = media.findById(id);
        if (file == null
            || !file.uploaderId().equals(Ids.canonical(senderId))
            || !file.conversationId().equals(Ids.canonical(conversationId))) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION,
                "Media was not uploaded by you to this conversation");
        }
        if (file.kind() != type) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION,
                "Media is " + file.kind() + ", not " + type);
        }
        String text = caption == null || caption.isEmpty() ? null : caption;
        return switch (type) {
            case IMAGE -> new MessageContent.Image(file.mediaId(), file.fileName(), file.mimeType(), file.size(),
                file.width(), file.height(), text);
            case VIDEO -> new MessageContent.Video(file.mediaId(), file.fileName(), file.mimeType(), file.size(), text);
            case DOCUMENT -> new MessageContent.Document(file.mediaId(), file.fileName(), file.mimeType(), file.size(), text);
            default -> throw new IllegalArgumentException("Not a media type: " + type);
        };
    }

    /**
     * The file behind a signed link. The link is the permission: no user is needed, so an img tag can load it.
     */
    public Download open(String mediaId, Long expires, String sig) {
        String id = Ids.canonical(mediaId);
        if (id == null || expires == null || !links.verify(id, expires, sig)) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.FORBIDDEN, "Link is not valid or has expired");
        }
        Media file = media.findById(id);
        Resource bytes = file == null ? null : store.get(id);
        if (bytes == null) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.NOT_FOUND, "Media not found");
        }
        return new Download(file, bytes, expires);
    }

    private long maxBytes(MessageType kind) {
        return switch (kind) {
            case IMAGE -> limits.maxImageSize().toBytes();
            case VIDEO -> limits.maxVideoSize().toBytes();
            default -> limits.maxDocumentSize().toBytes();
        };
    }

    private Dimensions checkImage(String mediaId, String mimeType) {
        Resource stored = store.get(mediaId);
        try {
            try (InputStream in = stored.getInputStream()) {
                if (!MediaTypes.looksLike(mimeType, in.readNBytes(HEAD_BYTES))) {
                    throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION,
                        "File content is not " + mimeType);
                }
            }
            return readDimensions(stored);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read upload", ex);
        }
    }

    /**
     * Reads only the header. Leaves the size unknown when the JDK has no reader for the type (WebP) or it fails.
     */
    private static Dimensions readDimensions(Resource stored) {
        try (InputStream in = stored.getInputStream(); ImageInputStream image = ImageIO.createImageInputStream(in)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(image);
            if (!readers.hasNext()) {
                return Dimensions.UNKNOWN;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(image, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                return width > 0 && height > 0 ? new Dimensions(width, height) : Dimensions.UNKNOWN;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            log.debug("Could not read image size", ex);
            return Dimensions.UNKNOWN;
        }
    }

    private record Dimensions(Integer width, Integer height) {
        static final Dimensions UNKNOWN = new Dimensions(null, null);
    }

    /**
     * expires is the link's own expiry, so caches never keep the file longer than the link allows.
     */
    public record Download(Media media, Resource bytes, long expires) {
    }
}
