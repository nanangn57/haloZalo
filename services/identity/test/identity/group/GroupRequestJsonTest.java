package identity.group;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GroupRequestJsonTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void createGroupRequestReadsName() throws Exception {
        CreateGroupRequest request = json.readValue("{\"name\":\"nhom\"}", CreateGroupRequest.class);

        assertEquals("nhom", request.getName());
        assertEquals(0, request.getMemberIds().size());
    }

    @Test
    void createGroupRequestReadsMemberIds() throws Exception {
        CreateGroupRequest request = json.readValue(
            "{\"name\":\"nhom\",\"memberIds\":[\"11111111-2222-4333-8444-555555555555\","
                + "\"aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee\"]}",
            CreateGroupRequest.class
        );

        assertEquals("nhom", request.getName());
        assertEquals(2, request.getMemberIds().size());
        assertEquals("11111111-2222-4333-8444-555555555555", request.getMemberIds().get(0));
    }

    @Test
    void addMemberRequestReadsUserId() throws Exception {
        AddMemberRequest request = json.readValue(
            "{\"userId\":\"11111111-2222-4333-8444-555555555555\"}",
            AddMemberRequest.class
        );

        assertEquals("11111111-2222-4333-8444-555555555555", request.getUserId());
    }

    @Test
    void missingFieldsStayNull() throws Exception {
        assertNull(json.readValue("{}", CreateGroupRequest.class).getName());
        assertEquals(0, json.readValue("{}", CreateGroupRequest.class).getMemberIds().size());
        assertNull(json.readValue("{}", AddMemberRequest.class).getUserId());
    }
}
