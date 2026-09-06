package io.vykronis.incident;

/**
 * What the verification window learned about a remediation: whether the target
 * service stayed healthy through the whole window (VERIFIED) or re-broke
 * (NOT_VERIFIED). Stored per incident in the {@code remediation_learn} table.
 */
public enum VerificationOutcome {
    VERIFIED,
    NOT_VERIFIED
}