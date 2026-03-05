package com.schedule.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScheduleBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScheduleBackendApplication.class, args);
    }
}