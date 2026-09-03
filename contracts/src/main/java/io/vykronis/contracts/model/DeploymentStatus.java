package io.vykronis.contracts.model;

/**
 * Lifecycle status of a deployment.
 */
public enum DeploymentStatus {
    DEPLOYING,
    SUCCESS,
    FAILED,
    ROLLED_BACK
}
