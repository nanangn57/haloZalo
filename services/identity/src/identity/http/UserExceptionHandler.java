package identity.http;

import identity.auth.ErrorResponse;
import identity.service.UserSearchService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class UserExceptionHandler {
    @ExceptionHandler(UserSearchService.Rejected.class)
    public ResponseEntity<ErrorResponse> rejected(UserSearchService.Rejected rejected) {
        HttpStatus status = switch (rejected.getReason()) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        String code = switch (rejected.getReason()) {
            case VALIDATION -> "VALIDATION_ERROR";
            case NOT_FOUND -> "NOT_FOUND";
        };
        return ResponseEntity.status(status).body(new ErrorResponse(code, rejected.getMessage()));
    }
}
