package http;

import event.RecordingEvents;
import member.ConversationMembers;
import member.StandaloneBlockChecker;
import member.StandaloneFriendshipChecker;
import member.StandaloneGroupMembers;
import conversation.ConversationType;
import service.MessageService;
import storage.MemoryConversations;
import storage.MemoryMessages;
import storage.MemoryReactions;
import storage.MemoryReadMarkers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SystemMessageControllerTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";
    private static final String GROUP = "cccccccc-0000-4000-8000-000000000002";

    private MockMvc mvc;
    private MessageService service;

    @BeforeEach
    void setUp() {
        MemoryConversations conversations = new MemoryConversations();
        StandaloneGroupMembers groups = new StandaloneGroupMembers();
        MemoryMessages messages = new MemoryMessages(conversations);
        service = new MessageService(conversations, messages, messages, new MemoryReactions(),
            new MemoryReadMarkers(messages), conversations.prefs(), new ConversationMembers(groups),
            new StandaloneFriendshipChecker(), new StandaloneBlockChecker(), new RecordingEvents());
        groups.replace(GROUP, Set.of(ALICE, BOB));
        service.openConversation(GROUP, ConversationType.GROUP, null);
        mvc = MockMvcBuilders.standaloneSetup(new MessageController(service))
            .setControllerAdvice(new MessageExceptionHandler())
            .setCustomArgumentResolvers(new UserIdResolver(new CurrentUser(true)))
            .build();
    }

    @Test
    void historyShowsASystemMessageWithTypeAndBodyButNoSenderId() throws Exception {
        service.postSystem(GROUP, "Alice added Bob");

        mvc.perform(get("/conversations/" + GROUP + "/messages").header(CurrentUser.HEADER, BOB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()").value(1))
            .andExpect(jsonPath("$.messages[0].type").value("SYSTEM"))
            .andExpect(jsonPath("$.messages[0].body").value("Alice added Bob"))
            .andExpect(jsonPath("$.messages[0].seq").value(1))
            .andExpect(jsonPath("$.messages[0].senderId").doesNotExist())
            .andExpect(jsonPath("$.messages[0].clientMsgId").isNotEmpty());
    }

    @Test
    void aClientCannotPostTypeSystem() throws Exception {
        mvc.perform(post("/conversations/" + GROUP + "/messages")
                .header(CurrentUser.HEADER, ALICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"SYSTEM\",\"body\":\"Alice added Bob\",\"clientMsgId\":\"99999999-8888-4777-8666-555555555555\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
