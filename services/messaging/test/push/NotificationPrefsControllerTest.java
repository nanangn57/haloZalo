package push;

import http.CurrentUser;
import http.MessageExceptionHandler;
import http.UserIdResolver;
import storage.MemoryNotificationPrefs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationPrefsControllerTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        NotificationPrefsService service = new NotificationPrefsService(new MemoryNotificationPrefs());
        mvc = MockMvcBuilders.standaloneSetup(new NotificationPrefsController(service))
            .setControllerAdvice(new MessageExceptionHandler())
            .setCustomArgumentResolvers(new UserIdResolver(new CurrentUser(true)))
            .build();
    }

    @Test
    void defaultsAreBothOn() throws Exception {
        mvc.perform(get("/me/notifications").header(CurrentUser.HEADER, ALICE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pushEnabled").value(true))
            .andExpect(jsonPath("$.previewEnabled").value(true));
    }

    @Test
    void patchChangesOnlyTheGivenFieldAndGetReadsItBack() throws Exception {
        sendPatch(ALICE, "{\"previewEnabled\":false}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pushEnabled").value(true))
            .andExpect(jsonPath("$.previewEnabled").value(false));
        sendPatch(ALICE, "{\"pushEnabled\":false}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pushEnabled").value(false))
            .andExpect(jsonPath("$.previewEnabled").value(false));

        mvc.perform(get("/me/notifications").header(CurrentUser.HEADER, ALICE))
            .andExpect(jsonPath("$.pushEnabled").value(false))
            .andExpect(jsonPath("$.previewEnabled").value(false));
    }

    @Test
    void prefsBelongToTheCaller() throws Exception {
        sendPatch(ALICE, "{\"pushEnabled\":false}").andExpect(status().isOk());

        mvc.perform(get("/me/notifications").header(CurrentUser.HEADER, BOB))
            .andExpect(jsonPath("$.pushEnabled").value(true));
    }

    @Test
    void emptyPatchOrWrongTypeIsRejected() throws Exception {
        sendPatch(ALICE, "{}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        sendPatch(ALICE, "{\"pushEnabled\":null}").andExpect(status().isBadRequest());
        sendPatch(ALICE, "{\"pushEnabled\":\"maybe\"}").andExpect(status().isBadRequest());
    }

    @Test
    void withoutAUserBothAreUnauthorized() throws Exception {
        mvc.perform(get("/me/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/me/notifications").contentType(MediaType.APPLICATION_JSON).content("{\"pushEnabled\":false}"))
            .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions sendPatch(String userId, String body) throws Exception {
        return mvc.perform(patch("/me/notifications")
            .header(CurrentUser.HEADER, userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }
}
