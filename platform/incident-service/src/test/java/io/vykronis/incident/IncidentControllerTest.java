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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IncidentControllerTest {

    @Mock
    private IncidentRepository repository;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new IncidentController(repository)).build();
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
}
