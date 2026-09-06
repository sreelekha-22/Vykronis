package io.vykronis.incident;

/**
 * An approval was requested for an incident that is not AWAITING_APPROVAL.
 * Mapped to a 409 CONFLICT.
 */
public class ApprovalNotAllowedException extends RuntimeException {
    public ApprovalNotAllowedException(String message) {
        super(message);
    }
}
