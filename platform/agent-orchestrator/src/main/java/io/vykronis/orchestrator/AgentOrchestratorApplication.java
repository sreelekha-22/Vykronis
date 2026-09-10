package io.vykronis.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgentOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentOrchestratorApplication.class, args);
    }
}
