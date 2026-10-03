package http;

import api.CatchUpResponse;
import api.ConversationResponse;
import api.ForwardMessageRequest;
import api.MessageResponse;
import api.OpenConversationRequest;
import api.ReactionsResponse;
import api.SendMessageRequest;
import conversation.ConversationType;
import message.MessageContent;
import service.MessageService;

import java.util.Arrays;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class MessageController {
    /**
     * The gateway checks the session with Identity and forwards the user id in this header.
     */
    public static final String USER_HEADER = "X-User-Id";

    private final MessageService messages;

    public MessageController(MessageService messages) {
        this.messages = messages;
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<MessageResponse> send(
        @RequestHeader(value = USER_HEADER, required = false) String userId,
        @PathVariable String conversationId,
        @RequestBody SendMessageRequest request) {
        MessageContent content = request.toContent();
        if (content == null) {
            throw new MessageService.Rejected(MessageService.Rejected.Reason.VALIDATION, "Message type must be TEXT or EMOTION");
        }
        MessageService.Sent sent = messages.send(
            conversationId, userId, request.getClientMsgId(), content, null, request.getReplyTo());
        return sentResponse(sent);
    }

    /**
     * Catch-up after a reconnect or a seq gap. Call again with the last seq received while hasMore is true.
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public CatchUpResponse catchUp(
        @RequestHeader(value = USER_HEADER, required = false) String userId,
        @PathVariable String conversationId,
        @RequestParam(required = false) Long afterSeq,
        @RequestParam(required = false) Integer limit) {
        return new CatchUpResponse(messages.catchUp(conversationId, userId, afterSeq, limit));
    }

    @PostMapping("/conversations/{conversationId}/forwards")
    public ResponseEntity<MessageResponse> forward(
        @RequestHeader(value = USER_HEADER, required = false) String userId,
        @PathVariable String conversationId,
        @RequestBody ForwardMessageRequest request) {
        return sentResponse(messages.forward(conversationId, userId, request.getClientMsgId(), request.getMessageId()));
    }

    @DeleteMapping("/conversations/{conversationId}/messages/{messageId}")
    public ResponseEntity<Void> delete(
        @RequestHeader(value = USER_HEADER, required = false) String userId,
        @PathVariable String conversationId,
        @PathVariable String messageId) {
        messages.delete(conversationId, messageId, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/conversations/{conversationId}/messages/{messageId}/reactions/{code}")
    public ResponseEntity<Void> react(
        @RequestHeader(value = USER_HEADER, required = false) String userId,
        @PathVariable String conversationId,
        @PathVariable String messageId,
        @PathVariable String code) {
        messages.react(conversationId, messageId, userId, code);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/conversations/{conversationId}/messages/{messageId}/reactions/{code}")
    public ResponseEntity<Void> unreact(
        @RequestHeader(value = USER_HEADER, required = false) String userId,
        @PathVariable String conversationId,
        @PathVariable String messageId,
        @PathVariable String code) {
        messages.unreact(conversationId, messageId, userId, code);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversations/{conversationId}/messages/{messageId}/reactions")
    public ReactionsResponse reactions(
        @RequestHeader(value = USER_HEADER, required = false) String userId,
        @PathVariable String conversationId,
        @PathVariable String messageId) {
        return new ReactionsResponse(messages.reactions(conversationId, messageId, userId));
    }

    @PostMapping("/internal/conversations")
    public ResponseEntity<ConversationResponse> open(@RequestBody OpenConversationRequest request) {
        ConversationType type = Arrays.stream(ConversationType.values())
            .filter(value -> value.name().equals(request.getType()))
            .findFirst()
            .orElse(null);
        return ResponseEntity.status(201).body(new ConversationResponse(
            messages.openConversation(request.getConversationId(), type, request.getMemberIds())));
    }

    private static ResponseEntity<MessageResponse> sentResponse(MessageService.Sent sent) {
        return ResponseEntity.status(sent.isCreated() ? 201 : 200).body(new MessageResponse(sent.getMessage()));
    }
}
