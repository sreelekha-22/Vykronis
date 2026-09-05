package io.vykronis.gateway;

import io.vykronis.gateway.auth.CurrentIdentity;
import io.vykronis.gateway.auth.Identity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiGatewayApplicationTests {

	@Autowired
	private CurrentIdentity currentIdentity;

	@Test
	void contextLoads() {
	}

	@Test
	void resolvesAnonymousIdentityThroughTheContextSeam() {
		Identity identity = currentIdentity
				.identityOf(MockServerWebExchange.from(MockServerHttpRequest.get("/api/incidents").build()));

		assertThat(identity.subject()).isEqualTo("anonymous");
	}

}