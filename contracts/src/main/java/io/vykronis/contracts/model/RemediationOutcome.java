package io.vykronis.contracts.model;

/**
 * Final outcome of an executed {@link RemediationCommand}, reported on the
 * {@code obs.remediation} topic and consumed by the incident service to drive
 * the incident into its verify window.
 */
public enum RemediationOutcome {
    /** The executor ran the remediation steps for the target successfully. */
    COMPLETED,
    /** The executor failed to remediate the target. */
    FAILED
}