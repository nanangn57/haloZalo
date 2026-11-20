package http;

import api.ErrorResponse;
import service.MessageService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public final class MessageExceptionHandler {
    @ExceptionHandler(MessageService.Rejected.class)
    public ResponseEntity<ErrorResponse> rejected(MessageService.Rejected rejected) {
        HttpStatus status = switch (rejected.getReason()) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
        };
        String code = switch (rejected.getReason()) {
            case VALIDATION -> "VALIDATION_ERROR";
            case UNAUTHENTICATED -> "UNAUTHENTICATED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case TOO_LARGE -> "PAYLOAD_TOO_LARGE";
        };
        return ResponseEntity.status(status).body(new ErrorResponse(code, rejected.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> mistyped(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", ex.getName() + " is not a valid number"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "Request body is not valid JSON"));
    }

    /**
     * Over spring.servlet.multipart.max-file-size, before any per-kind limit is checked.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(new ErrorResponse("PAYLOAD_TOO_LARGE", "File is too large"));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ErrorResponse> noFile(Exception ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "A multipart part named file is required"));
    }
}
