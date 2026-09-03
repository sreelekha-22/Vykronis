package io.vykronis.correlation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafkaStreams;

@SpringBootApplication
@EnableKafkaStreams
public class CorrelationEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CorrelationEngineApplication.class, args);
    }
}
