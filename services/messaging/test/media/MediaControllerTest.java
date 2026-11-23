package media;

import event.EnvelopeEventPublisher;
import http.CurrentUser;
import http.MessageExceptionHandler;
import http.UserIdResolver;
import member.ConversationMembers;
import member.StandaloneGroupMembers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import storage.MemoryConversations;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaControllerTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";

    @TempDir
    Path temp;

    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        EnvelopeEventPublisher events = new EnvelopeEventPublisher(
            new MemoryConversations(), new ConversationMembers(new StandaloneGroupMembers()), frame -> { });
        MediaController controller = new MediaController(new LocalMediaStore(temp.toString(), "http://localhost:8080"), events);
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new MessageExceptionHandler())
            .setCustomArgumentResolvers(new UserIdResolver(new CurrentUser(true)))
            .build();
    }

    @Test
    void audioUploadAcceptsAudioMimeTypesIncludingCodecParameters() throws Exception {
        upload("audio/mp4", "AUDIO", new byte[1024])
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("AUDIO"))
            .andExpect(jsonPath("$.contentType").value("audio/mp4"))
            .andExpect(jsonPath("$.url").isNotEmpty());
        upload("audio/webm;codecs=opus", "audio", new byte[1024])
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("AUDIO"));
    }

    @Test
    void audioUploadIsLimitedTo5MBAndToAudioTypes() throws Exception {
        upload("audio/mpeg", "AUDIO", new byte[5 * 1024 * 1024]).andExpect(status().isOk());
        upload("audio/mpeg", "AUDIO", new byte[5 * 1024 * 1024 + 1])
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        upload("application/pdf", "AUDIO", new byte[10])
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message").value("Unsupported audio content type"));
        upload("audio/mpeg", "AUDIO", new byte[0]).andExpect(status().isBadRequest());
    }

    @Test
    void otherTypesStillWorkAndUnknownTypeIsRejected() throws Exception {
        upload("application/pdf", "DOCUMENT", new byte[10]).andExpect(status().isOk());
        upload("audio/mpeg", "VOICE", new byte[10]).andExpect(status().isBadRequest());
    }

    @Test
    void baseTypeDropsParametersAndCase() {
        assertEquals("audio/webm", MediaController.baseType("Audio/WebM; codecs=opus"));
        assertEquals("audio/mp4", MediaController.baseType("audio/mp4"));
    }

    private ResultActions upload(String contentType, String type, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/media/uploads")
            .file(new MockMultipartFile("file", "voice", contentType, bytes))
            .param("type", type)
            .header(CurrentUser.HEADER, ALICE));
    }
}
