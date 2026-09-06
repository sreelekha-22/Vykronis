package io.vykronis.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

import java.util.TimeZone;

@SpringBootApplication
@EnableKafka
public class EventServiceApplication {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(EventServiceApplication.class, args);
    }
}
