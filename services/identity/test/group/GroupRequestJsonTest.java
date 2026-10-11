package group;

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
        assertNull(json.readValue("{}", AddMemberRequest.class).getUserId());
    }
}
