package io.vykronis.incident;

/**
 * Lifecycle of an incident. The Phase 2 path only exercises OPEN plus the
 * terminal states upstream of remediation; later phases add AWAITING_APPROVAL,
 * REMEDIATING and VERIFYING.
 */
public enum IncidentStatus {
    OPEN,
    INVESTIGATING,
    HYPOTHESIS_READY,
    AWAITING_APPROVAL,
    AUTO_APPROVED,
    REMEDIATING,
    VERIFYING,
    RESOLVED,
    FAILED
}
