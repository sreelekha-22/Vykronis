package io.vykronis.schemaregistry.registry;

import java.util.List;

/** Result of validating a payload against the latest registered schema of a topic. */
public record ValidationReport(String topic, int version, boolean valid, List<String> errors) {

    public static ValidationReport valid(String topic, int version) {
        return new ValidationReport(topic, version, true, List.of());
    }

    public static ValidationReport invalid(String topic, int version, List<String> errors) {
        return new ValidationReport(topic, version, false, errors);
    }
}