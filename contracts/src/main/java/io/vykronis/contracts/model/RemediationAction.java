package io.vykronis.contracts.model;

/**
 * The remedial operation a {@link RemediationCommand} carries out.
 */
public enum RemediationAction {
    /** Revert a deployment / restore the previous healthy state. */
    ROLLBACK,
    /** Restart the service. */
    RESTART
}