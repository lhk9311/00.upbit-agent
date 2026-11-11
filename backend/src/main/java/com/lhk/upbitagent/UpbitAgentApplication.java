package com.lhk.upbitagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UpbitAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(UpbitAgentApplication.class, args);
    }
}
