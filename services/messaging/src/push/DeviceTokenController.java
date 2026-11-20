package push;

import http.UserId;
import service.MessageService;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class DeviceTokenController {
    private final DeviceTokenRepository tokens;

    public DeviceTokenController(DeviceTokenRepository tokens) {
        this.tokens = tokens;
    }

    @PostMapping("/devices")
    public ResponseEntity<Void> register(@UserId String userId, @RequestBody DeviceBody body) {
        requireUser(userId);
        if (body == null || body.token() == null || body.token().isBlank()) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION, "token is required");
        }
        tokens.upsert(userId, body.token().trim(), body.platform());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/devices")
    public ResponseEntity<Void> unregister(@UserId String userId, @RequestBody DeviceBody body) {
        requireUser(userId);
        if (body == null || body.token() == null || body.token().isBlank()) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION, "token is required");
        }
        tokens.delete(userId, body.token().trim());
        return ResponseEntity.noContent().build();
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.UNAUTHENTICATED, "Missing or invalid access token");
        }
    }

    public record DeviceBody(
        @JsonProperty("token") String token,
        @JsonProperty("platform") String platform
    ) {
    }
}
