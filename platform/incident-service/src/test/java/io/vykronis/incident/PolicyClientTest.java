package io.vykronis.incident;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The incident-service side of the policy gate: one POST to the policy
 * service's {@code /api/policy/evaluate} carrying the incident env + the
 * acting subject. Non-2xx surfaces as {@link PolicyUnavailableException}
 * (the incident never transitions into a partial policy state).
 */
class PolicyClientTest {

    private PolicyClient clientWith(RestClient.Builder builder) {
        return new PolicyClient(builder, "http://policy:8086");
    }

    @Test
    void postsTheEvaluationRequestAndReturnsTheDecision() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = clientWith(builder);

        server.expect(requestTo("http://policy:8086/api/policy/evaluate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(
                        "{\"incidentId\":\"3f2c1a7e-1b2c-4d3e-8f4a-1b2c3d4e5f6a\","
                                + "\"action\":\"ROLLBACK\",\"environment\":\"PROD\","
                                + "\"subject\":{\"name\":\"ops\",\"roles\":[\"vykronis-approver\"],\"service\":false}}"))
                .andRespond(withSuccess(
                        "{\"decision\":\"REQUIRE_APPROVAL\",\"reason\":\"prod remediation needs an approver\","
                                + "\"auditSequence\":7,\"auditedAt\":\"2026-09-05T12:00:00Z\"}",
                        MediaType.APPLICATION_JSON));

        PolicyEvaluation evaluation = client.evaluate(
                "3f2c1a7e-1b2c-4d3e-8f4a-1b2c3d4e5f6a", "ROLLBACK", "PROD",
                OperatorActor.user("ops", "vykronis-approver"));

        assertThat(evaluation.decision()).isEqualTo("REQUIRE_APPROVAL");
        assertThat(evaluation.reason()).isEqualTo("prod remediation needs an approver");
        server.verify();
    }

    @Test
    void surfacesPolicyServiceFailuresAsUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = clientWith(builder);

        server.expect(requestTo("http://policy:8086/api/policy/evaluate"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.evaluate(
                "3f2c1a7e-1b2c-4d3e-8f4a-1b2c3d4e5f6a", "ROLLBACK", "PROD",
                OperatorActor.user("ops", "vykronis-approver")))
                .isInstanceOf(PolicyUnavailableException.class);
        server.verify();
    }
}