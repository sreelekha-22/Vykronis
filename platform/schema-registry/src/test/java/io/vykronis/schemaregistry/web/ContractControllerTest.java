package io.vykronis.schemaregistry.web;

import io.vykronis.schemaregistry.registry.SchemaEntry;
import io.vykronis.schemaregistry.registry.SchemaRegistryService;
import io.vykronis.schemaregistry.registry.ValidationReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContractControllerTest {

    private final SchemaRegistryService service = mock(SchemaRegistryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ContractController(service)).build();
    }

    private static SchemaEntry entry(String topic, int version) {
        return new SchemaEntry(topic, version,
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                        .put("$schema", "https://json-schema.org/draft/2020-12/schema")
                        .put("$id", "io.vykronis.schema-registry.test.schema.json"),
                Instant.now());
    }

    @Test
    void listsTheLatestSchemaPerTopic() throws Exception {
        when(service.summary()).thenReturn(List.of(entry("obs.alerts", 1), entry("obs.metrics", 3)));
        mvc.perform(get("/contracts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].topic").value("obs.alerts"))
                .andExpect(jsonPath("$[0].latestVersion").value(1))
                .andExpect(jsonPath("$[1].latestVersion").value(3))
                .andExpect(jsonPath("$[1].registeredAt").exists());
    }

    @Test
    void servesTheLatestSchemaWithSchemaMediaType() throws Exception {
        when(service.latest("obs.metrics")).thenReturn(Optional.of(entry("obs.metrics", 2)));
        mvc.perform(get("/contracts/obs.metrics/latest"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/schema+json"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "io.vykronis.schema-registry.test.schema.json")));
    }

    @Test
    void latestVersionAndSpecificVersionsAreNotFoundForUnknownTopics() throws Exception {
        when(service.latest("obs.ghost")).thenReturn(Optional.empty());
        when(service.version(eq("obs.ghost"), anyInt())).thenReturn(Optional.empty());
        mvc.perform(get("/contracts/obs.ghost/latest")).andExpect(status().isNotFound());
        mvc.perform(get("/contracts/obs.ghost/version")).andExpect(status().isNotFound());
        mvc.perform(get("/contracts/obs.ghost/versions/1")).andExpect(status().isNotFound());
    }

    @Test
    void reportsTheLatestVersionNumber() throws Exception {
        when(service.latest("obs.metrics")).thenReturn(Optional.of(entry("obs.metrics", 7)));
        mvc.perform(get("/contracts/obs.metrics/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("obs.metrics"))
                .andExpect(jsonPath("$.version").value(7));
    }

    @Test
    void servesASpecificVersion() throws Exception {
        when(service.version("obs.metrics", 1)).thenReturn(Optional.of(entry("obs.metrics", 1)));
        mvc.perform(get("/contracts/obs.metrics/versions/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/schema+json"));
    }

    @Test
    void registersANewVersionWith201() throws Exception {
        when(service.register(eq("obs.metrics"), any(), any(Instant.class)))
                .thenReturn(entry("obs.metrics", 4));
        mvc.perform(post("/contracts/obs.metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"$schema\": \"https://json-schema.org/draft/2020-12/schema\", \"type\": \"object\" }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.topic").value("obs.metrics"))
                .andExpect(jsonPath("$.version").value(4));
    }

    @Test
    void rejectsSchemasTheRegistryDoesNotAccept() throws Exception {
        when(service.register(eq("obs.metrics"), any(), any(Instant.class)))
                .thenThrow(new IllegalArgumentException("schema must be a JSON Schema object (missing or malformed $schema)"));
        mvc.perform(post("/contracts/obs.metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("$schema")));
    }

    @Test
    void rejectsMalformedOrEmptyRequestBodies() throws Exception {
        mvc.perform(post("/contracts/obs.metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("valid JSON")));

        mvc.perform(post("/contracts/obs.metrics/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("empty")));
    }

    @Test
    void validatesConformingPayloads() throws Exception {
        when(service.validate(eq("obs.metrics"), any())).thenReturn(
                ValidationReport.valid("obs.metrics", 1));
        mvc.perform(post("/contracts/obs.metrics/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"550e8400-e29b-41d4-a716-446655440000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void flagsNonConformingPayloadsWithAClientError() throws Exception {
        when(service.validate(eq("obs.metrics"), any())).thenReturn(
                ValidationReport.invalid("obs.metrics", 1, List.of("$.id: does not match the uuid format")));
        mvc.perform(post("/contracts/obs.metrics/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0]").value("$.id: does not match the uuid format"));
    }
}