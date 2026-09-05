package io.vykronis.orchestrator.ai;

/**
 * Supported values of {@code vykronis.ai.provider} — config-only selection of
 * the investigation LLM. {@link #NONE} is the default and keeps the platform
 * fully functional without any LLM; GROQ, HUGGINGFACE and OPENROUTER are hosted
 * OpenAI-compatible endpoints.
 */
public enum AiProviderName {

    NONE,
    OLLAMA,
    GROQ,
    GEMINI,
    HUGGINGFACE,
    OPENROUTER
}