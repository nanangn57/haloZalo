package realtime;

import http.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserHandshakeInterceptorTest {
    @Test
    void upgradeWithoutUserIsRefusedWith401() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = handshake(new MockHttpServletRequest("GET", "/ws"), response, attributes);

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
        assertTrue(attributes.isEmpty());
    }

    @Test
    void upgradeWithUserKeepsTheUserOnTheSocket() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws");
        request.addHeader(CurrentUser.HEADER, "aaaaaaaa-0000-4000-8000-000000000001");
        Map<String, Object> attributes = new HashMap<>();

        assertTrue(handshake(request, new MockHttpServletResponse(), attributes));
        assertEquals("aaaaaaaa-0000-4000-8000-000000000001", attributes.get(UserHandshakeInterceptor.USER_ID));
    }

    @Test
    void theAppFiltersUserWinsAndTheHeaderCanBeTurnedOff() {
        MockHttpServletRequest fromFilter = new MockHttpServletRequest("GET", "/ws");
        fromFilter.setAttribute(CurrentUser.ATTRIBUTE, "AAAAAAAA-0000-4000-8000-000000000002");
        fromFilter.addHeader(CurrentUser.HEADER, "aaaaaaaa-0000-4000-8000-000000000001");
        Map<String, Object> attributes = new HashMap<>();

        assertTrue(handshake(fromFilter, new MockHttpServletResponse(), attributes, true));
        assertEquals("aaaaaaaa-0000-4000-8000-000000000002", attributes.get(UserHandshakeInterceptor.USER_ID));

        MockHttpServletRequest headerOnly = new MockHttpServletRequest("GET", "/ws");
        headerOnly.addHeader(CurrentUser.HEADER, "aaaaaaaa-0000-4000-8000-000000000001");
        MockHttpServletResponse refused = new MockHttpServletResponse();
        assertFalse(handshake(headerOnly, refused, new HashMap<>(), false));
        assertEquals(401, refused.getStatus());
    }

    private static boolean handshake(MockHttpServletRequest request, MockHttpServletResponse response, Map<String, Object> attributes) {
        return handshake(request, response, attributes, true);
    }

    private static boolean handshake(
        MockHttpServletRequest request, MockHttpServletResponse response, Map<String, Object> attributes, boolean headerEnabled) {
        ServletServerHttpResponse servletResponse = new ServletServerHttpResponse(response);
        boolean allowed = new UserHandshakeInterceptor(new CurrentUser(headerEnabled)).beforeHandshake(
            new ServletServerHttpRequest(request), servletResponse, new TextWebSocketHandler(), attributes);
        servletResponse.close();
        return allowed;
    }
}
