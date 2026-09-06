package io.vykronis.incident;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;

import java.util.TimeZone;

@SpringBootApplication
@EnableKafka
@EnableJpaRepositories
public class IncidentServiceApplication {

    static {
        // Postgres 16 rejects non-canonical IANA names (e.g. "Asia/Calcutta") that
        // host JVMs may default to; pin UTC so the JDBC TimeZone startup parameter
        // is always valid regardless of the launcher's locale.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(IncidentServiceApplication.class, args);
    }
}
