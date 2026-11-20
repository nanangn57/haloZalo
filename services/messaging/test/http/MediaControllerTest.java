package http;

import event.RecordingEvents;
import media.LocalMediaStore;
import media.MediaProperties;
import media.TestLinks;
import member.ConversationMembers;
import member.StandaloneGroupMembers;
import service.MediaService;
import service.MessageService;
import storage.MemoryConversations;
import storage.MemoryMedia;
import storage.MemoryMessages;
import storage.MemoryReactions;
import storage.MemoryReadMarkers;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Upload, send, read back and download, through the controllers with in-memory storage and real files on disk.
 */
class MediaControllerTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";
    private static final String CAROL = "aaaaaaaa-0000-4000-8000-000000000003";
    private static final String DIRECT = "cccccccc-0000-4000-8000-000000000001";
    private static final String GROUP = "cccccccc-0000-4000-8000-000000000002";

    @TempDir
    Path mediaDir;

    private MockMvc mvc;
    private RecordingEvents events;
    private byte[] png;

    @BeforeEach
    void setUp() throws Exception {
        MemoryConversations conversations = new MemoryConversations();
        StandaloneGroupMembers groups = new StandaloneGroupMembers();
        groups.replace(GROUP, java.util.Set.of(ALICE, BOB, CAROL));
        MemoryMessages messages = new MemoryMessages(conversations);
        events = new RecordingEvents();
        MessageService service = new MessageService(conversations, messages, new MemoryReactions(),
            new MemoryReadMarkers(messages), new ConversationMembers(groups), events);
        service.openConversation(DIRECT, conversation.ConversationType.DIRECT, List.of(ALICE, BOB));
        service.openConversation(GROUP, conversation.ConversationType.GROUP, null);
        // Small video limit so the per-kind limit can be hit without a big test file.
        MediaProperties limits = new MediaProperties(mediaDir.toString(), "", Duration.ofHours(1),
            DataSize.ofMegabytes(10), DataSize.ofBytes(1024), DataSize.ofMegabytes(10));
        MediaService media = new MediaService(service, new MemoryMedia(), new LocalMediaStore(mediaDir), TestLinks.LINKS,
            limits, events);
        mvc = MockMvcBuilders.standaloneSetup(
                new MessageController(service, media, TestLinks.LINKS), new MediaController(media, TestLinks.LINKS))
            .setControllerAdvice(new MessageExceptionHandler())
            .setCustomArgumentResolvers(new UserIdResolver(new CurrentUser(true)))
            .build();
        png = png(3, 2);
    }

    @Test
    void uploadReturnsMediaWithSignedLinkAndImageSize() throws Exception {
        upload(DIRECT, ALICE, new MockMultipartFile("file", "cat.png", "image/png", png))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mediaId").isNotEmpty())
            .andExpect(jsonPath("$.kind").value("IMAGE"))
            .andExpect(jsonPath("$.fileName").value("cat.png"))
            .andExpect(jsonPath("$.mimeType").value("image/png"))
            .andExpect(jsonPath("$.size").value(png.length))
            .andExpect(jsonPath("$.width").value(3))
            .andExpect(jsonPath("$.height").value(2))
            .andExpect(jsonPath("$.url", startsWith("/media/")))
            .andExpect(jsonPath("$.url", containsString("sig=")));

        assertEquals(1, events.uploads.size());
        assertEquals(ALICE, events.uploads.get(0).uploaderId());
        // Uploading alone sends nothing.
        assertEquals(0, events.created.size());
    }

    @Test
    void sentImageCarriesCaptionAndAFreshLinkThatDownloadsTheBytes() throws Exception {
        String mediaId = mediaId(upload(DIRECT, ALICE, new MockMultipartFile("file", "cat.png", "image/png", png)));

        MvcResult sent = sendMedia(DIRECT, ALICE, "IMAGE", mediaId, "look")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("IMAGE"))
            .andExpect(jsonPath("$.body").value("look"))
            .andExpect(jsonPath("$.media.mediaId").value(mediaId))
            .andExpect(jsonPath("$.media.kind").value("IMAGE"))
            .andExpect(jsonPath("$.media.width").value(3))
            .andReturn();
        assertEquals(1, events.created.size());

        // Bob reads history and downloads with the link he was given, with no user header.
        MvcResult history = mvc.perform(get("/conversations/" + DIRECT + "/messages").header("X-User-Id", BOB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[0].media.mediaId").value(mediaId))
            .andReturn();
        String url = JsonPath.read(history.getResponse().getContentAsString(), "$.messages[0].media.url");
        MvcResult file = mvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("inline")))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("private")))
            .andReturn();
        assertArrayEquals(png, file.getResponse().getContentAsByteArray());
        assertEquals(url, JsonPath.read(sent.getResponse().getContentAsString(), "$.media.url"));
    }

    @Test
    void documentWithoutBrowserTypeIsKnownByExtensionAndAlwaysDownloads() throws Exception {
        byte[] pdf = "%PDF-1.4 tiny".getBytes();
        MvcResult uploaded = upload(GROUP, CAROL,
                new MockMultipartFile("file", "C:\\Users\\carol\\spec v2.pdf", "application/octet-stream", pdf))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("DOCUMENT"))
            .andExpect(jsonPath("$.mimeType").value("application/pdf"))
            .andExpect(jsonPath("$.fileName").value("spec v2.pdf"))
            .andExpect(jsonPath("$.width").doesNotExist())
            .andReturn();

        sendMedia(GROUP, CAROL, "DOCUMENT", mediaId(uploaded), null)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.body").doesNotExist())
            .andExpect(jsonPath("$.media.fileName").value("spec v2.pdf"));
        mvc.perform(get(url(uploaded)))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment")));
    }

    @Test
    void videoSupportsRangeRequestsSoPlayersCanSeek() throws Exception {
        byte[] video = new byte[600];
        MvcResult uploaded = upload(DIRECT, BOB, new MockMultipartFile("file", "clip.mp4", "video/mp4", video))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("VIDEO"))
            .andReturn();

        mvc.perform(get(url(uploaded)).header(HttpHeaders.RANGE, "bytes=0-99"))
            .andExpect(status().isPartialContent())
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-99/600"));
    }

    @Test
    void uploadRejectsNonMembersUnsupportedTypesFakeImagesEmptyAndTooLargeFiles() throws Exception {
        upload(DIRECT, CAROL, new MockMultipartFile("file", "cat.png", "image/png", png))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        upload(DIRECT, ALICE, new MockMultipartFile("file", "x.svg", "image/svg+xml", "<svg/>".getBytes()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message").value("File type is not supported"));
        upload(DIRECT, ALICE, new MockMultipartFile("file", "x.html", "text/html", "<script>".getBytes()))
            .andExpect(status().isBadRequest());
        upload(DIRECT, ALICE, new MockMultipartFile("file", "cat.png", "image/png", "<html>".getBytes()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message").value("File content is not image/png"));
        upload(DIRECT, ALICE, new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message").value("File is empty"));
        upload(DIRECT, ALICE, new MockMultipartFile("file", "big.mp4", "video/mp4", new byte[1025]))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
        mvc.perform(multipart("/conversations/" + DIRECT + "/media")
                .file(new MockMultipartFile("other", "a.txt", "text/plain", "x".getBytes()))
                .header("X-User-Id", ALICE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertEquals(0, events.uploads.size());
        // Rejected uploads leave no file behind.
        try (var files = java.nio.file.Files.walk(mediaDir)) {
            assertEquals(0, files.filter(java.nio.file.Files::isRegularFile).count());
        }
    }

    @Test
    void onlyTheUploaderCanSendTheMediaOnlyInItsConversationAndOnlyAsItsKind() throws Exception {
        String mediaId = mediaId(upload(GROUP, ALICE, new MockMultipartFile("file", "cat.png", "image/png", png)));

        sendMedia(GROUP, BOB, "IMAGE", mediaId, null).andExpect(status().isBadRequest());
        sendMedia(DIRECT, ALICE, "IMAGE", mediaId, null).andExpect(status().isBadRequest());
        sendMedia(GROUP, ALICE, "VIDEO", mediaId, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message").value("Media is IMAGE, not VIDEO"));
        sendMedia(GROUP, ALICE, "IMAGE", "not-a-uuid", null).andExpect(status().isBadRequest());
        sendMedia(GROUP, ALICE, "IMAGE", UUID.randomUUID().toString(), null).andExpect(status().isBadRequest());
        sendMedia(GROUP, ALICE, "IMAGE", mediaId, null).andExpect(status().isCreated());
    }

    @Test
    void forwardedMediaKeepsItsFileAndDeletedMediaHidesIt() throws Exception {
        String mediaId = mediaId(upload(GROUP, ALICE, new MockMultipartFile("file", "cat.png", "image/png", png)));
        String messageId = JsonPath.read(sendMedia(GROUP, ALICE, "IMAGE", mediaId, "hi")
            .andReturn().getResponse().getContentAsString(), "$.messageId");

        mvc.perform(post("/conversations/" + DIRECT + "/forwards")
                .header("X-User-Id", BOB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageId\":\"" + messageId + "\",\"clientMsgId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("IMAGE"))
            .andExpect(jsonPath("$.media.mediaId").value(mediaId))
            .andExpect(jsonPath("$.forwardedFrom").value(messageId));

        mvc.perform(delete("/conversations/" + GROUP + "/messages/" + messageId).header("X-User-Id", ALICE))
            .andExpect(status().isNoContent());
        mvc.perform(get("/conversations/" + GROUP + "/messages").header("X-User-Id", BOB))
            .andExpect(jsonPath("$.messages[0].status").value("DELETED"))
            .andExpect(jsonPath("$.messages[0].media").doesNotExist())
            .andExpect(jsonPath("$.messages[0].body").doesNotExist());
    }

    @Test
    void downloadRejectsMissingOrTamperedSignatures() throws Exception {
        MvcResult uploaded = upload(DIRECT, ALICE, new MockMultipartFile("file", "cat.png", "image/png", png)).andReturn();
        String mediaId = mediaId(uploaded);
        String url = url(uploaded);

        mvc.perform(get("/media/" + mediaId)).andExpect(status().isForbidden());
        mvc.perform(get(url.replace("sig=", "sig=x"))).andExpect(status().isForbidden());
        String expires = url.replaceAll(".*expires=(\\d+).*", "$1");
        mvc.perform(get(url.replace("expires=" + expires, "expires=" + (Long.parseLong(expires) + 600))))
            .andExpect(status().isForbidden());
        // A valid signature for another id does not open this one.
        String other = mediaId(upload(DIRECT, ALICE, new MockMultipartFile("file", "dog.png", "image/png", png)));
        mvc.perform(get(url.replace(mediaId, other))).andExpect(status().isForbidden());
    }

    private ResultActions upload(String conversationId, String userId, MockMultipartFile file) throws Exception {
        return mvc.perform(multipart("/conversations/" + conversationId + "/media").file(file).header("X-User-Id", userId));
    }

    private ResultActions sendMedia(String conversationId, String userId, String type, String mediaId, String caption)
        throws Exception {
        String body = "{\"type\":\"" + type + "\",\"mediaId\":\"" + mediaId + "\",\"clientMsgId\":\"" + UUID.randomUUID() + "\""
            + (caption == null ? "" : ",\"body\":\"" + caption + "\"") + "}";
        return mvc.perform(post("/conversations/" + conversationId + "/messages")
            .header("X-User-Id", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private static String mediaId(ResultActions upload) throws Exception {
        return mediaId(upload.andReturn());
    }

    private static String mediaId(MvcResult upload) throws Exception {
        return JsonPath.read(upload.getResponse().getContentAsString(), "$.mediaId");
    }

    private static String url(MvcResult upload) throws Exception {
        return JsonPath.read(upload.getResponse().getContentAsString(), "$.url");
    }

    private static byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
