package http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import service.MessageService;
import storage.MemoryConversations;
import storage.MemoryMessages;
import storage.MemoryReactions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MessageControllerTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";
    private static final String CAROL = "aaaaaaaa-0000-4000-8000-000000000003";
    private static final String DIRECT = "cccccccc-0000-4000-8000-000000000001";
    private static final String CLIENT_MSG_ID = "99999999-8888-4777-8666-555555555555";

    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        MessageService service = new MessageService(
            new MemoryConversations(), new MemoryMessages(), new MemoryReactions(), message -> { });
        mvc = MockMvcBuilders.standaloneSetup(new MessageController(service))
            .setControllerAdvice(new MessageExceptionHandler())
            .build();
        mvc.perform(post("/internal/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":\"" + DIRECT + "\",\"type\":\"DIRECT\",\"memberIds\":[\"" + ALICE + "\",\"" + BOB + "\"]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("DIRECT"))
            .andExpect(jsonPath("$.memberIds.length()").value(2));
    }

    @Test
    void sendReturnsContractMessageAndRetryReturnsSameMessage() throws Exception {
        send(ALICE, "{\"type\":\"TEXT\",\"body\":\"hello\",\"clientMsgId\":\"" + CLIENT_MSG_ID + "\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.messageId").isNotEmpty())
            .andExpect(jsonPath("$.conversationId").value(DIRECT))
            .andExpect(jsonPath("$.seq").value(1))
            .andExpect(jsonPath("$.senderId").value(ALICE))
            .andExpect(jsonPath("$.type").value("TEXT"))
            .andExpect(jsonPath("$.body").value("hello"))
            .andExpect(jsonPath("$.clientMsgId").value(CLIENT_MSG_ID))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.content").doesNotExist())
            .andExpect(jsonPath("$.status").doesNotExist());

        send(ALICE, "{\"type\":\"TEXT\",\"body\":\"hello\",\"clientMsgId\":\"" + CLIENT_MSG_ID + "\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.seq").value(1));
    }

    @Test
    void sendAcceptsEmotion() throws Exception {
        send(BOB, "{\"type\":\"EMOTION\",\"body\":\"wow\",\"clientMsgId\":\"" + CLIENT_MSG_ID + "\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("EMOTION"))
            .andExpect(jsonPath("$.body").value("wow"));
    }

    @Test
    void sendRejectsTypesTheContractDoesNotHaveYet() throws Exception {
        send(ALICE, "{\"type\":\"IMAGE\",\"body\":\"https://cdn/a.png\",\"clientMsgId\":\"" + CLIENT_MSG_ID + "\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void sendRejectsMissingUserAndNonMember() throws Exception {
        mvc.perform(post("/conversations/" + DIRECT + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"TEXT\",\"body\":\"hi\",\"clientMsgId\":\"" + CLIENT_MSG_ID + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        send(CAROL, "{\"type\":\"TEXT\",\"body\":\"hi\",\"clientMsgId\":\"" + CLIENT_MSG_ID + "\"}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void sendRejectsTextOver4096BytesAndBrokenJson() throws Exception {
        send(ALICE, "{\"type\":\"TEXT\",\"body\":\"" + "a".repeat(4097) + "\",\"clientMsgId\":\"" + CLIENT_MSG_ID + "\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message").value("Text body exceeds 4096 bytes"));

        send(ALICE, "{not json")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private ResultActions send(String userId, String body) throws Exception {
        return mvc.perform(post("/conversations/" + DIRECT + "/messages")
            .header(MessageController.USER_HEADER, userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }
}
