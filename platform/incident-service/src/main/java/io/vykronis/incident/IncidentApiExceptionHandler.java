package io.vykronis.incident;

import io.vykronis.common.api.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Incident-specific errors on top of the shared {@code common-web} advice
 * (which handles the generic 400/500 cases). 404/409 must NOT fall through to
 * the generic 500 handler, hence these explicit mappings.
 */
@RestControllerAdvice
public class IncidentApiExceptionHandler {

    @ExceptionHandler(IncidentNotFoundException.class)
    public ResponseEntity<ApiError> onMissing(IncidentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(InvestigationNotAllowedException.class)
    public ResponseEntity<ApiError> onConflict(InvestigationNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "INVESTIGATION_NOT_ALLOWED", ex.getMessage()));
    }
}