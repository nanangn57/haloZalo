package http;

import auth.ErrorResponse;
import service.AuthService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class AuthExceptionHandler {
    @ExceptionHandler(AuthService.Rejected.class)
    public ResponseEntity<ErrorResponse> rejected(AuthService.Rejected rejected) {
        HttpStatus status = switch (rejected.getReason()) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
        };
        String code = switch (rejected.getReason()) {
            case VALIDATION -> "VALIDATION_ERROR";
            case CONFLICT -> "CONFLICT";
            case UNAUTHENTICATED -> "UNAUTHENTICATED";
        };
        return ResponseEntity.status(status).body(new ErrorResponse(code, rejected.getMessage()));
    }
}