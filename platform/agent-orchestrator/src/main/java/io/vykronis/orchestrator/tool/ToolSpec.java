package io.vykronis.orchestrator.tool;

import java.util.List;

/**
 * Immutable description of an agent tool, used to build the allow-listed tool
 * catalogue offered to the AI provider (and later the rule-based fallback).
 *
 * @param id           stable tool identifier referenced by decisions (e.g. {@code evidence.search})
 * @param description  purpose, in plain language
 * @param parameters   argument names the tool accepts ({@code ?} suffix = optional)
 */
public record ToolSpec(String id, String description, List<String> parameters) {
}