package io.vykronis.incident;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IncidentControllerTest {

    @Mock
    private IncidentRepository repository;

    @Mock
    private InvestigationClient investigationClient;

    private MockMvc mockMvc() {
        IncidentService service = new IncidentService(repository, investigationClient);
        return MockMvcBuilders.standaloneSetup(new IncidentController(service))
                .setControllerAdvice(new IncidentApiExceptionHandler())
                .build();
    }

    private Incident incident(String serviceId, String status) {
        return new Incident(
                "inc-" + serviceId,
                "correlation-engine",
                serviceId,
                "PROD",
                "HIGH",
                status,
                "High error rate on " + serviceId,
                "desc",
                55.0,
                42,
                Instant.parse("2026-09-03T10:00:00Z"),
                Instant.parse("2026-09-03T10:01:00Z"),
                null,
                null);
    }

    @Test
    void listReturnsAllWhenNoStatusFilter() throws Exception {
        when(repository.findAllByOrderByDetectedAtDesc())
                .thenReturn(List.of(incident("payment-service", "OPEN"), incident("order-service", "RESOLVED")));

        mockMvc().perform(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void listFiltersByStatus() throws Exception {
        when(repository.findByStatusOrderByDetectedAtDesc("OPEN"))
                .thenReturn(List.of(incident("payment-service", "OPEN")));

        mockMvc().perform(get("/api/incidents").param("status", "open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status", is("OPEN")));
    }

    @Test
    void detailReturnsIncidentWhenFound() throws Exception {
        when(repository.findByIncidentId("inc-1"))
                .thenReturn(Optional.of(incident("payment-service", "OPEN")));

        mockMvc().perform(get("/api/incidents/inc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId", is("inc-payment-service")))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.errorRate", is(55.0)));
    }

    @Test
    void detailReturnsNotFoundWhenMissing() throws Exception {
        when(repository.findByIncidentId("nope")).thenReturn(Optional.empty());

        mockMvc().perform(get("/api/incidents/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void investigateTransitionsToHypothesisReadyAndReturnsIt() throws Exception {
        Incident incident = incident("payment-service", "OPEN");
        String hypothesis = "{\"statement\":\"suspected deployment\",\"confidence\":0.5,"
                + "\"affectedServiceId\":\"payment-service\",\"source\":\"fallback\","
                + "\"evidence\":[{\"eventId\":\"ev-1\",\"type\":\"LOG\",\"source\":\"payment\"}]}";
        when(repository.findByIncidentId("inc-payment-service")).thenReturn(Optional.of(incident));
        when(investigationClient.investigate("inc-payment-service")).thenReturn(hypothesis);
        when(repository.save(incident)).thenReturn(incident);

        String body = mockMvc().perform(post("/api/incidents/inc-payment-service/investigate"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode node = io.vykronis.common.json.Json.mapper().readTree(body);
        assertThat(node.path("status").asText()).isEqualTo("HYPOTHESIS_READY");
        assertThat(node.path("hypothesis").path("statement").asText()).isEqualTo("suspected deployment");
    }

    @Test
    void investigateRejectsIncidentInNonInvestigableState() throws Exception {
        Incident incident = incident("payment-service", "RESOLVED");
        when(repository.findByIncidentId("inc-payment-service")).thenReturn(Optional.of(incident));

        mockMvc().perform(post("/api/incidents/inc-payment-service/investigate"))
                .andExpect(status().isConflict());

        org.mockito.Mockito.verify(investigationClient, org.mockito.Mockito.never())
                .investigate(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void investigateReturnsNotFoundForUnknownIncident() throws Exception {
        when(repository.findByIncidentId("nope")).thenReturn(Optional.empty());

        mockMvc().perform(post("/api/incidents/nope/investigate"))
                .andExpect(status().isNotFound());
    }
}
