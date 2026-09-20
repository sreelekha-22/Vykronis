package io.vykronis.remediation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class RemediationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RemediationServiceApplication.class, args);
    }
}
