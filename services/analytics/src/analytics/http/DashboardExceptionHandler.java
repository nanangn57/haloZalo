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

    @ExceptionHandler(StatsController.ForbiddenStats.class)
    public ResponseEntity<ErrorResponse> forbidden(StatsController.ForbiddenStats forbidden) {
        String message = forbidden.getMessage();
        if (message != null && message.startsWith("Missing")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHENTICATED", message));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("FORBIDDEN", message));
    }
}
