package http;

import event.RecordingEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import service.MessageService;
import storage.MemoryConversations;
import storage.MemoryMessages;
import storage.MemoryReactions;

import com.jayway.jsonpath.JsonPath;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MessageControllerTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";
    private static final String CAROL = "aaaaaaaa-0000-4000-8000-000000000003";
    private static final String DIRECT = "cccccccc-0000-4000-8000-000000000001";
    private static final String GROUP = "cccccccc-0000-4000-8000-000000000002";
    private static final String CLIENT_MSG_ID = "99999999-8888-4777-8666-555555555555";

    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        MessageService service = new MessageService(
            new MemoryConversations(), new MemoryMessages(), new MemoryReactions(), new RecordingEvents());
        mvc = MockMvcBuilders.standaloneSetup(new MessageController(service))
            .setControllerAdvice(new MessageExceptionHandler())
            .build();
        mvc.perform(post("/internal/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":\"" + DIRECT + "\",\"type\":\"DIRECT\",\"memberIds\":[\"" + ALICE + "\",\"" + BOB + "\"]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("DIRECT"))
            .andExpect(jsonPath("$.memberIds.length()").value(2));
        mvc.perform(post("/internal/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":\"" + GROUP + "\",\"type\":\"GROUP\",\"memberIds\":[\""
                    + ALICE + "\",\"" + BOB + "\",\"" + CAROL + "\"]}"))
            .andExpect(status().isCreated());
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
            .andExpect(jsonPath("$.replyTo").doesNotExist())
            .andExpect(jsonPath("$.forwardedFrom").doesNotExist())
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

    @Test
    void replyReturnsReplyToAndMustStayInTheSameConversation() throws Exception {
        String original = messageId(sendText(DIRECT, ALICE, "question"));

        send(BOB, "{\"type\":\"TEXT\",\"body\":\"answer\",\"clientMsgId\":\"" + CLIENT_MSG_ID
                + "\",\"replyTo\":\"" + original + "\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.replyTo").value(original))
            .andExpect(jsonPath("$.seq").value(2));

        send(GROUP, BOB, "{\"type\":\"TEXT\",\"body\":\"answer\",\"clientMsgId\":\"" + UUID.randomUUID()
                + "\",\"replyTo\":\"" + original + "\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void forwardCreatesANewMessageThatPointsAtTheOriginal() throws Exception {
        String original = messageId(sendText(DIRECT, ALICE, "news"));
        String forwardBody = "{\"messageId\":\"" + original + "\",\"clientMsgId\":\"" + CLIENT_MSG_ID + "\"}";

        MvcResult forwarded = forward(GROUP, BOB, forwardBody)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.conversationId").value(GROUP))
            .andExpect(jsonPath("$.senderId").value(BOB))
            .andExpect(jsonPath("$.seq").value(1))
            .andExpect(jsonPath("$.type").value("TEXT"))
            .andExpect(jsonPath("$.body").value("news"))
            .andExpect(jsonPath("$.forwardedFrom").value(original))
            .andReturn();

        forward(GROUP, BOB, forwardBody)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messageId").value(messageId(forwarded)))
            .andExpect(jsonPath("$.seq").value(1));
    }

    @Test
    void forwardRejectsSourceTheSenderCannotSeeAndUnknownSource() throws Exception {
        String original = messageId(sendText(DIRECT, ALICE, "secret"));

        forward(GROUP, CAROL, "{\"messageId\":\"" + original + "\",\"clientMsgId\":\"" + CLIENT_MSG_ID + "\"}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        forward(GROUP, BOB, "{\"messageId\":\"" + UUID.randomUUID() + "\",\"clientMsgId\":\"" + CLIENT_MSG_ID + "\"}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void deleteIsForTheSenderOnlyAndRepeatsSafely() throws Exception {
        String message = messageId(sendText(DIRECT, ALICE, "oops"));

        deleteMessage(DIRECT, message, BOB)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        deleteMessage(DIRECT, message, ALICE).andExpect(status().isNoContent());
        deleteMessage(DIRECT, message, ALICE).andExpect(status().isNoContent());

        forward(GROUP, BOB, "{\"messageId\":\"" + message + "\",\"clientMsgId\":\"" + CLIENT_MSG_ID + "\"}")
            .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRejectsMissingUserWrongConversationAndUnknownMessage() throws Exception {
        String message = messageId(sendText(DIRECT, ALICE, "hi"));

        mvc.perform(delete("/conversations/" + DIRECT + "/messages/" + message))
            .andExpect(status().isUnauthorized());
        deleteMessage(GROUP, message, ALICE)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        deleteMessage(DIRECT, UUID.randomUUID().toString(), ALICE)
            .andExpect(status().isNotFound());
    }

    @Test
    void reactionsCanBeAddedListedAndRemoved() throws Exception {
        String message = messageId(sendText(GROUP, ALICE, "party"));

        react(GROUP, message, "love", BOB).andExpect(status().isNoContent());
        react(GROUP, message, "love", BOB).andExpect(status().isNoContent());
        react(GROUP, message, "love", CAROL).andExpect(status().isNoContent());
        react(GROUP, message, "haha", CAROL).andExpect(status().isNoContent());

        reactions(GROUP, message, ALICE)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reactions.length()").value(3))
            .andExpect(jsonPath("$.reactions[0].userId").value(BOB))
            .andExpect(jsonPath("$.reactions[0].code").value("love"))
            .andExpect(jsonPath("$.reactions[0].createdAt").isNotEmpty())
            .andExpect(jsonPath("$.reactions[0].messageId").doesNotExist());

        unreact(GROUP, message, "haha", CAROL).andExpect(status().isNoContent());
        unreact(GROUP, message, "haha", CAROL).andExpect(status().isNoContent());

        reactions(GROUP, message, BOB)
            .andExpect(jsonPath("$.reactions.length()").value(2))
            .andExpect(jsonPath("$.reactions[1].userId").value(CAROL))
            .andExpect(jsonPath("$.reactions[1].code").value("love"));
    }

    @Test
    void reactionsRejectUnknownCodeOutsidersWrongConversationAndDeletedMessage() throws Exception {
        String message = messageId(sendText(DIRECT, ALICE, "private"));

        react(DIRECT, message, "heart", BOB)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        react(DIRECT, message, "like", CAROL)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        reactions(DIRECT, message, CAROL).andExpect(status().isForbidden());
        react(GROUP, message, "like", BOB)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(put("/conversations/" + DIRECT + "/messages/" + message + "/reactions/like"))
            .andExpect(status().isUnauthorized());

        deleteMessage(DIRECT, message, ALICE).andExpect(status().isNoContent());
        react(DIRECT, message, "like", BOB).andExpect(status().isBadRequest());
    }

    private ResultActions react(String conversationId, String messageId, String code, String userId) throws Exception {
        return mvc.perform(put(reactionPath(conversationId, messageId) + "/" + code)
            .header(MessageController.USER_HEADER, userId));
    }

    private ResultActions unreact(String conversationId, String messageId, String code, String userId) throws Exception {
        return mvc.perform(delete(reactionPath(conversationId, messageId) + "/" + code)
            .header(MessageController.USER_HEADER, userId));
    }

    private ResultActions reactions(String conversationId, String messageId, String userId) throws Exception {
        return mvc.perform(get(reactionPath(conversationId, messageId))
            .header(MessageController.USER_HEADER, userId));
    }

    private static String reactionPath(String conversationId, String messageId) {
        return "/conversations/" + conversationId + "/messages/" + messageId + "/reactions";
    }

    @Test
    void catchUpReturnsContractMessagesInSeqOrderWithPaging() throws Exception {
        String first = messageId(sendText(DIRECT, ALICE, "one"));
        sendText(DIRECT, BOB, "two");
        sendText(DIRECT, ALICE, "three");

        catchUp(DIRECT, BOB, "?afterSeq=0&limit=2")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasMore").value(true))
            .andExpect(jsonPath("$.messages.length()").value(2))
            .andExpect(jsonPath("$.messages[0].messageId").value(first))
            .andExpect(jsonPath("$.messages[0].seq").value(1))
            .andExpect(jsonPath("$.messages[0].type").value("TEXT"))
            .andExpect(jsonPath("$.messages[0].body").value("one"))
            .andExpect(jsonPath("$.messages[0].clientMsgId").isNotEmpty())
            .andExpect(jsonPath("$.messages[0].status").doesNotExist())
            .andExpect(jsonPath("$.messages[1].seq").value(2));

        catchUp(DIRECT, BOB, "?afterSeq=2&limit=2")
            .andExpect(jsonPath("$.hasMore").value(false))
            .andExpect(jsonPath("$.messages.length()").value(1))
            .andExpect(jsonPath("$.messages[0].body").value("three"));

        catchUp(DIRECT, BOB, "")
            .andExpect(jsonPath("$.messages.length()").value(3));
    }

    @Test
    void catchUpShowsADeletedMessageAsAnEmptySlot() throws Exception {
        String gone = messageId(sendText(DIRECT, ALICE, "secret"));
        deleteMessage(DIRECT, gone, ALICE).andExpect(status().isNoContent());

        catchUp(DIRECT, BOB, "?afterSeq=0")
            .andExpect(jsonPath("$.messages[0].messageId").value(gone))
            .andExpect(jsonPath("$.messages[0].seq").value(1))
            .andExpect(jsonPath("$.messages[0].status").value("DELETED"))
            .andExpect(jsonPath("$.messages[0].deletedAt").isNotEmpty())
            .andExpect(jsonPath("$.messages[0].body").doesNotExist());
    }

    @Test
    void catchUpRejectsBadParametersOutsidersAndMissingUser() throws Exception {
        catchUp(DIRECT, BOB, "?afterSeq=abc")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        catchUp(DIRECT, BOB, "?limit=500")
            .andExpect(status().isBadRequest());
        catchUp(DIRECT, CAROL, "")
            .andExpect(status().isForbidden());
        mvc.perform(get("/conversations/" + DIRECT + "/messages"))
            .andExpect(status().isUnauthorized());
    }

    private ResultActions catchUp(String conversationId, String userId, String query) throws Exception {
        return mvc.perform(get("/conversations/" + conversationId + "/messages" + query)
            .header(MessageController.USER_HEADER, userId));
    }

    private ResultActions send(String userId, String body) throws Exception {
        return send(DIRECT, userId, body);
    }

    private ResultActions send(String conversationId, String userId, String body) throws Exception {
        return mvc.perform(post("/conversations/" + conversationId + "/messages")
            .header(MessageController.USER_HEADER, userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private MvcResult sendText(String conversationId, String userId, String text) throws Exception {
        return send(conversationId, userId,
                "{\"type\":\"TEXT\",\"body\":\"" + text + "\",\"clientMsgId\":\"" + UUID.randomUUID() + "\"}")
            .andExpect(status().isCreated())
            .andReturn();
    }

    private ResultActions forward(String conversationId, String userId, String body) throws Exception {
        return mvc.perform(post("/conversations/" + conversationId + "/forwards")
            .header(MessageController.USER_HEADER, userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private ResultActions deleteMessage(String conversationId, String messageId, String userId) throws Exception {
        return mvc.perform(delete("/conversations/" + conversationId + "/messages/" + messageId)
            .header(MessageController.USER_HEADER, userId));
    }

    private static String messageId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.messageId");
    }
}
