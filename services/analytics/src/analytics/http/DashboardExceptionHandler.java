package analytics.http;

import analytics.query.DashboardService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class DashboardExceptionHandler {
    @ExceptionHandler(DashboardService.MissingSession.class)
    public ResponseEntity<ErrorResponse> missing(DashboardService.MissingSession missing) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("UNAUTHENTICATED", missing.getMessage()));
    }
}
