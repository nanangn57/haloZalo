package http;

import event.RecordingEvents;
import member.ConversationMembers;
import member.StandaloneBlockChecker;
import member.StandaloneFriendshipChecker;
import member.StandaloneGroupMembers;

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
import storage.MemoryReadMarkers;

import com.jayway.jsonpath.JsonPath;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prefs, requests inbox, search, audio and block over HTTP.
 */
class ConversationFeaturesControllerTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";
    private static final String CAROL = "aaaaaaaa-0000-4000-8000-000000000003";

    private MockMvc mvc;
    private StandaloneFriendshipChecker friendships;
    private StandaloneBlockChecker blocks;

    @BeforeEach
    void setUp() {
        MemoryConversations conversations = new MemoryConversations();
        MemoryMessages messages = new MemoryMessages(conversations);
        friendships = new StandaloneFriendshipChecker();
        blocks = new StandaloneBlockChecker();
        MessageService service = new MessageService(conversations, messages, messages, new MemoryReactions(),
            new MemoryReadMarkers(messages), conversations.prefs(), new ConversationMembers(new StandaloneGroupMembers()),
            friendships, blocks, new RecordingEvents());
        mvc = MockMvcBuilders.standaloneSetup(new MessageController(service))
            .setControllerAdvice(new MessageExceptionHandler())
            .setCustomArgumentResolvers(new UserIdResolver(new CurrentUser(true)))
            .build();
    }

    @Test
    void patchPrefsReturnsTheCallersPrefsAndListShowsThem() throws Exception {
        friendships.befriend(ALICE, BOB);
        String id = openDirect(ALICE, BOB);

        mvc.perform(patch("/conversations/" + id + "/prefs")
                .header(CurrentUser.HEADER, ALICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pinned\":true,\"muted\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationId").value(id))
            .andExpect(jsonPath("$.pinned").value(true))
            .andExpect(jsonPath("$.muted").value(true))
            .andExpect(jsonPath("$.archived").value(false));

        list(ALICE, "")
            .andExpect(jsonPath("$.conversations[0].pinned").value(true))
            .andExpect(jsonPath("$.conversations[0].muted").value(true))
            .andExpect(jsonPath("$.conversations[0].archived").value(false))
            .andExpect(jsonPath("$.conversations[0].pendingRequest").value(false));
        list(BOB, "")
            .andExpect(jsonPath("$.conversations[0].pinned").value(false))
            .andExpect(jsonPath("$.conversations[0].muted").value(false));
    }

    @Test
    void patchPrefsRejectsEmptyBodyOutsidersAndMissingUser() throws Exception {
        friendships.befriend(ALICE, BOB);
        String id = openDirect(ALICE, BOB);

        mvc.perform(patch("/conversations/" + id + "/prefs")
                .header(CurrentUser.HEADER, ALICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(patch("/conversations/" + id + "/prefs")
                .header(CurrentUser.HEADER, CAROL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pinned\":true}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/conversations/" + id + "/prefs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pinned\":true}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(patch("/conversations/" + id + "/prefs")
                .header(CurrentUser.HEADER, ALICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pinned\":\"yes\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void archivedQueryListsOnlyArchivedAndDefaultSkipsThem() throws Exception {
        friendships.befriend(ALICE, BOB);
        String id = openDirect(ALICE, BOB);
        patchPrefs(id, ALICE, "{\"archived\":true}");

        list(ALICE, "").andExpect(jsonPath("$.conversations.length()").value(0));
        list(ALICE, "?archived=true")
            .andExpect(jsonPath("$.conversations.length()").value(1))
            .andExpect(jsonPath("$.conversations[0].conversationId").value(id))
            .andExpect(jsonPath("$.conversations[0].archived").value(true));
        list(BOB, "").andExpect(jsonPath("$.conversations.length()").value(1));
    }

    @Test
    void strangerRequestFlowOverHttp() throws Exception {
        String id = openDirect(ALICE, BOB);

        list(BOB, "").andExpect(jsonPath("$.conversations.length()").value(0));
        list(BOB, "?inbox=requests")
            .andExpect(jsonPath("$.conversations.length()").value(1))
            .andExpect(jsonPath("$.conversations[0].pendingRequest").value(true));
        list(ALICE, "").andExpect(jsonPath("$.conversations[0].pendingRequest").value(false));
        list(ALICE, "?inbox=requests").andExpect(jsonPath("$.conversations.length()").value(0));

        mvc.perform(post("/conversations/" + id + "/accept-request").header(CurrentUser.HEADER, BOB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationId").value(id))
            .andExpect(jsonPath("$.pendingRequest").value(false));

        list(BOB, "").andExpect(jsonPath("$.conversations.length()").value(1));
        list(BOB, "?inbox=requests").andExpect(jsonPath("$.conversations.length()").value(0));
    }

    @Test
    void acceptRequestRejectsOutsidersAndMissingUser() throws Exception {
        String id = openDirect(ALICE, BOB);

        mvc.perform(post("/conversations/" + id + "/accept-request").header(CurrentUser.HEADER, CAROL))
            .andExpect(status().isForbidden());
        mvc.perform(post("/conversations/" + id + "/accept-request"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownInboxIsAValidationError() throws Exception {
        mvc.perform(get("/conversations?inbox=spam").header(CurrentUser.HEADER, ALICE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void blockedPairGets403OnOpenAndSend() throws Exception {
        friendships.befriend(ALICE, BOB);
        String id = openDirect(ALICE, BOB);
        blocks.block(BOB, ALICE);

        mvc.perform(post("/conversations/direct")
                .header(CurrentUser.HEADER, ALICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + BOB + "\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        send(id, ALICE, "{\"type\":\"TEXT\",\"body\":\"hi\"")
            .andExpect(status().isForbidden());
    }

    @Test
    void searchFiltersTextIgnoringCaseAndKeepsPagination() throws Exception {
        friendships.befriend(ALICE, BOB);
        String id = openDirect(ALICE, BOB);
        for (String body : new String[] {"Hello World", "nothing", "say HELLO again", "bye"}) {
            send(id, ALICE, "{\"type\":\"TEXT\",\"body\":\"" + body + "\"")
                .andExpect(status().isCreated());
        }

        mvc.perform(get("/conversations/" + id + "/messages?q=hello").header(CurrentUser.HEADER, BOB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()").value(2))
            .andExpect(jsonPath("$.messages[0].body").value("Hello World"))
            .andExpect(jsonPath("$.messages[1].body").value("say HELLO again"))
            .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get("/conversations/" + id + "/messages?q=hello&limit=1").header(CurrentUser.HEADER, BOB))
            .andExpect(jsonPath("$.messages.length()").value(1))
            .andExpect(jsonPath("$.messages[0].body").value("say HELLO again"))
            .andExpect(jsonPath("$.hasMore").value(true));
        mvc.perform(get("/conversations/" + id + "/messages?q=hello&afterSeq=1").header(CurrentUser.HEADER, BOB))
            .andExpect(jsonPath("$.messages.length()").value(1));
        mvc.perform(get("/conversations/" + id + "/messages?q=zzz").header(CurrentUser.HEADER, BOB))
            .andExpect(jsonPath("$.messages.length()").value(0));
        mvc.perform(get("/conversations/" + id + "/messages?q=" + "x".repeat(257)).header(CurrentUser.HEADER, BOB))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/conversations/" + id + "/messages?q=hello").header(CurrentUser.HEADER, CAROL))
            .andExpect(status().isForbidden());
    }

    @Test
    void audioMessageRoundTripsAndValidates() throws Exception {
        friendships.befriend(ALICE, BOB);
        String id = openDirect(ALICE, BOB);

        send(id, ALICE, "{\"type\":\"AUDIO\",\"body\":\"https://cdn/v.m4a\",\"mimeType\":\"audio/mp4\",\"size\":2048,\"duration\":7")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("AUDIO"))
            .andExpect(jsonPath("$.body").value("https://cdn/v.m4a"));
        mvc.perform(get("/conversations/" + id + "/messages").header(CurrentUser.HEADER, BOB))
            .andExpect(jsonPath("$.messages[0].type").value("AUDIO"))
            .andExpect(jsonPath("$.messages[0].body").value("https://cdn/v.m4a"));

        send(id, ALICE, "{\"type\":\"AUDIO\",\"body\":\"https://cdn/v.m4a\",\"mimeType\":\"audio/mp4\",\"size\":5242881")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        send(id, ALICE, "{\"type\":\"AUDIO\",\"body\":\"https://cdn/v.m4a\",\"mimeType\":\"video/mp4\",\"size\":10")
            .andExpect(status().isBadRequest());
        send(id, ALICE, "{\"type\":\"AUDIO\",\"body\":\"https://cdn/v.m4a\",\"size\":10")
            .andExpect(status().isBadRequest());
    }

    private String openDirect(String from, String to) throws Exception {
        String body = mvc.perform(post("/conversations/direct")
                .header(CurrentUser.HEADER, from)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + to + "\"}"))
            .andExpect(status().is2xxSuccessful())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.conversationId");
    }

    private ResultActions list(String userId, String query) throws Exception {
        return mvc.perform(get("/conversations" + query).header(CurrentUser.HEADER, userId))
            .andExpect(status().isOk());
    }

    private void patchPrefs(String id, String userId, String body) throws Exception {
        mvc.perform(patch("/conversations/" + id + "/prefs")
                .header(CurrentUser.HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
    }

    /**
     * bodyStart is the JSON without its closing brace; clientMsgId is added.
     */
    private ResultActions send(String id, String userId, String bodyStart) throws Exception {
        return mvc.perform(post("/conversations/" + id + "/messages")
            .header(CurrentUser.HEADER, userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(bodyStart + ",\"clientMsgId\":\"" + UUID.randomUUID() + "\"}"));
    }
}
