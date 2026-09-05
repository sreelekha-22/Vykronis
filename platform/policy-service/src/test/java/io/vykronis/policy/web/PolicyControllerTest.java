package io.vykronis.policy.web;

import io.vykronis.common.web.ApiExceptionHandler;
import io.vykronis.policy.PolicyService;
import io.vykronis.policy.audit.InMemoryAuditLog;
import io.vykronis.policy.matrix.DecisionMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PolicyControllerTest {

    private static final String APPROVER_BODY = """
            {"incidentId":"1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
             "action":"ROLLBACK","environment":"PROD",
             "subject":{"name":"ops","roles":["vykronis-user","vykronis-approver"],"service":false}}
            """;

    private InMemoryAuditLog auditLog;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        auditLog = new InMemoryAuditLog();
        PolicyService service = new PolicyService(DecisionMatrix.standard(), auditLog);
        mvc = MockMvcBuilders.standaloneSetup(new PolicyController(service, auditLog))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void evaluatesRollbackInProdByApprover() throws Exception {
        mvc.perform(post("/api/policy/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPROVER_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REQUIRE_APPROVAL"))
                .andExpect(jsonPath("$.incidentId").value("1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed"))
                .andExpect(jsonPath("$.auditSequence").value(1));
    }

    @Test
    void evaluatesRollbackInDevAsAutomatic() throws Exception {
        mvc.perform(post("/api/policy/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"ROLLBACK","environment":"DEV",
                                 "subject":{"name":"alice","roles":["vykronis-user"],"service":false}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOW"));
    }

    @Test
    void rejectsMissingFieldsWith400() throws Exception {
        mvc.perform(post("/api/policy/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"environment":"DEV",
                                 "subject":{"name":"x","roles":[],"service":false}}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void auditEndpointExposesAppendOnlyLogInOrder() throws Exception {
        mvc.perform(post("/api/policy/evaluate")
                        .contentType(MediaType.APPLICATION_JSON).content(APPROVER_BODY))
                .andExpect(status().isOk());
        mvc.perform(post("/api/policy/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"ROLLBACK","environment":"DEV",
                                 "subject":{"name":"alice","roles":["vykronis-user"],"service":false}}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/policy/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sequence").value(1))
                .andExpect(jsonPath("$[0].decision").value("REQUIRE_APPROVAL"))
                .andExpect(jsonPath("$[1].sequence").value(2))
                .andExpect(jsonPath("$[1].decision").value("ALLOW"))
                .andExpect(jsonPath("$[0].previousHash").value("0"));
    }
}