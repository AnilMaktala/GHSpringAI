package com.example.triage;

import com.example.triage.config.TriageConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableRetry
@EnableConfigurationProperties(TriageConfiguration.class)
public class GithubIssueTriageApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(GithubIssueTriageApplication.class, args);
    }
}
