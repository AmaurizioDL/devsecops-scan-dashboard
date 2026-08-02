package com.devsecops.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
public class DashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(DashboardApplication.class, args);
    }

    /**
     * Runs the (single, at-a-time) background scan job. A single thread is enough:
     * ScanRunRegistry already rejects a second concurrent scan with 409, so there's
     * never more than one task to run at once.
     */
    @Bean
    public ExecutorService scanExecutorService() {
        return Executors.newSingleThreadExecutor();
    }
}
