package identity.http;

import identity.auth.ErrorResponse;
import identity.service.FriendService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class FriendExceptionHandler {
    @ExceptionHandler(FriendService.Rejected.class)
    public ResponseEntity<ErrorResponse> rejected(FriendService.Rejected rejected) {
        HttpStatus status = switch (rejected.getReason()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case VALIDATION -> HttpStatus.BAD_REQUEST;
        };
        String code = switch (rejected.getReason()) {
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case FORBIDDEN -> "FORBIDDEN";
            case VALIDATION -> "VALIDATION_ERROR";
        };
        return ResponseEntity.status(status).body(new ErrorResponse(code, rejected.getMessage()));
    }
}
