package com.portfoliomanager.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MarketDataWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketDataWorkerApplication.class, args);
    }
}
