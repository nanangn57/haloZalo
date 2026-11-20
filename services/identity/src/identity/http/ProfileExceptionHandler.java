package identity.http;

import identity.auth.ErrorResponse;
import identity.service.ProfileService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class ProfileExceptionHandler {
    @ExceptionHandler(ProfileService.Rejected.class)
    public ResponseEntity<ErrorResponse> rejected(ProfileService.Rejected rejected) {
        HttpStatus status = rejected.getReason() == ProfileService.Rejected.Reason.NOT_FOUND
            ? HttpStatus.NOT_FOUND
            : HttpStatus.BAD_REQUEST;
        String code = rejected.getReason() == ProfileService.Rejected.Reason.NOT_FOUND
            ? "NOT_FOUND"
            : "VALIDATION_ERROR";
        return ResponseEntity.status(status).body(new ErrorResponse(code, rejected.getMessage()));
    }
}
