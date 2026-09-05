package io.vykronis.orchestrator.hypothesis;

/**
 * Who produced a hypothesis: the AI provider or the deterministic rule-based
 * fallback. Serialized lowercase per {@code hypothesis.schema.json}.
 */
public enum HypothesisSource {

    @com.fasterxml.jackson.annotation.JsonProperty("ai")
    AI,

    @com.fasterxml.jackson.annotation.JsonProperty("fallback")
    FALLBACK
}