package io.vykronis.orchestrator.investigation;

import io.vykronis.common.api.ApiError;
import io.vykronis.orchestrator.fallback.InsufficientEvidenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Investigation-specific error mapping on top of {@code common-web}'s shared
 * {@code ApiExceptionHandler}. A {@code RestControllerAdvice} with a declared
 * {@code ExceptionHandler} for a specific type wins over the shared generic
 * {@code Exception} handler.
 */
@RestControllerAdvice
public class InvestigationApiExceptionHandler {

    @ExceptionHandler(IncidentNotFoundException.class)
    public ResponseEntity<ApiError> onIncidentNotFound(IncidentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "INCIDENT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> onIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(InsufficientEvidenceException.class)
    public ResponseEntity<ApiError> onInsufficientEvidence(InsufficientEvidenceException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of(422, "INSUFFICIENT_EVIDENCE", ex.getMessage()));
    }
}