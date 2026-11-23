package presence;

import http.CurrentUser;
import http.MessageExceptionHandler;
import http.UserIdResolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PresenceControllerTest {
    private static final String ALICE = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String BOB = "aaaaaaaa-0000-4000-8000-000000000002";
    private static final String CAROL = "aaaaaaaa-0000-4000-8000-000000000003";

    private MockMvc mvc;
    private PresenceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PresenceRegistry(Clock.fixed(Instant.parse("2026-11-22T09:00:00Z"), ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(new PresenceController(registry))
            .setControllerAdvice(new MessageExceptionHandler())
            .setCustomArgumentResolvers(new UserIdResolver(new CurrentUser(true)))
            .build();
    }

    @Test
    void returnsOnlineAndOfflineInTheOrderAskedAndOmitsLastSeenForUsersNeverSeen() throws Exception {
        registry.connected(BOB);
        registry.connected(CAROL);
        registry.disconnected(CAROL);

        mvc.perform(get("/presence?ids=" + BOB + "," + CAROL + "," + ALICE).header(CurrentUser.HEADER, ALICE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.presences.length()").value(3))
            .andExpect(jsonPath("$.presences[0].userId").value(BOB))
            .andExpect(jsonPath("$.presences[0].online").value(true))
            .andExpect(jsonPath("$.presences[0].lastSeenAt").value("2026-11-22T09:00:00Z"))
            .andExpect(jsonPath("$.presences[1].userId").value(CAROL))
            .andExpect(jsonPath("$.presences[1].online").value(false))
            .andExpect(jsonPath("$.presences[1].lastSeenAt").value("2026-11-22T09:00:00Z"))
            .andExpect(jsonPath("$.presences[2].userId").value(ALICE))
            .andExpect(jsonPath("$.presences[2].online").value(false))
            .andExpect(jsonPath("$.presences[2].lastSeenAt").doesNotExist());
    }

    @Test
    void duplicateIdsAreAnsweredOnce() throws Exception {
        mvc.perform(get("/presence?ids=" + BOB + "," + BOB.toUpperCase()).header(CurrentUser.HEADER, ALICE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.presences.length()").value(1));
    }

    @Test
    void withoutAUserIsUnauthorized() throws Exception {
        mvc.perform(get("/presence?ids=" + BOB))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void missingBlankOrMalformedIdsAreRejected() throws Exception {
        mvc.perform(get("/presence").header(CurrentUser.HEADER, ALICE)).andExpect(status().isBadRequest());
        mvc.perform(get("/presence?ids=").header(CurrentUser.HEADER, ALICE)).andExpect(status().isBadRequest());
        mvc.perform(get("/presence?ids=,").header(CurrentUser.HEADER, ALICE)).andExpect(status().isBadRequest());
        mvc.perform(get("/presence?ids=" + BOB + ",nope").header(CurrentUser.HEADER, ALICE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void moreThanOneHundredIdsAreRejected() throws Exception {
        String ids = IntStream.range(0, 101)
            .mapToObj(i -> String.format("aaaaaaaa-0000-4000-8000-%012d", i))
            .collect(Collectors.joining(","));

        mvc.perform(get("/presence?ids=" + ids).header(CurrentUser.HEADER, ALICE))
            .andExpect(status().isBadRequest());
    }
}
