package io.vykronis.orchestrator.investigation;

import io.vykronis.orchestrator.fallback.InsufficientEvidenceException;
import io.vykronis.orchestrator.hypothesis.Hypothesis;
import io.vykronis.orchestrator.hypothesis.HypothesisSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exposes the orchestrator business over HTTP so the incident-service can
 * POST an investigation: 200 with the hypothesis JSON, 404 when the incident
 * cannot be loaded, 422 when there is not enough evidence, 400 on a bad body.
 */
class InvestigationControllerTest {

    private static final UUID INCIDENT = UUID.fromString("3f2c1a7e-1b2c-4d3e-8f4a-1b2c3d4e5f6a");

    private final InvestigationOrchestrator orchestrator = mock(InvestigationOrchestrator.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InvestigationController(orchestrator))
                .setControllerAdvice(new InvestigationApiExceptionHandler())
                .build();
    }

    private String body(String incidentId) {
        return "{\"incidentId\":\"" + incidentId + "\"}";
    }

    @Test
    void investigateReturnsTheHypothesis() throws Exception {
        Hypothesis hypothesis = new Hypothesis(INCIDENT, "suspected deployment",
                "payment-service 2026-09-05T010203Z", 0.5, "payment-service",
                "1.2.3", HypothesisSource.FALLBACK,
                List.of());
        when(orchestrator.investigate(INCIDENT)).thenReturn(hypothesis);

        mockMvc().perform(post("/api/investigations").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body(INCIDENT.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statement", is("suspected deployment")))
                .andExpect(jsonPath("$.source", is("fallback")));
    }

    @Test
    void investigateRejectsAMissingIncidentId() throws Exception {
        mockMvc().perform(post("/api/investigations").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void investigateRejectsAnInvalidIncidentId() throws Exception {
        mockMvc().perform(post("/api/investigations").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body("not-a-uuid")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void investigateMapsUnloadableIncidentToNotFound() throws Exception {
        when(orchestrator.investigate(INCIDENT))
                .thenThrow(new IncidentNotFoundException(INCIDENT));

        mockMvc().perform(post("/api/investigations").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body(INCIDENT.toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    void investigateMapsMissingEvidenceToUnprocessable() throws Exception {
        when(orchestrator.investigate(INCIDENT))
                .thenThrow(new InsufficientEvidenceException("no evidence for " + INCIDENT));

        mockMvc().perform(post("/api/investigations").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body(INCIDENT.toString())))
                .andExpect(status().isUnprocessableEntity());
    }

    private MockMvc mockMvc() {
        return mockMvc;
    }
}