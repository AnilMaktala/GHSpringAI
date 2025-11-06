package com.example.triage;

import com.example.triage.service.TriageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "triage.run-on-startup", havingValue = "true")
public class ManualTriageRunner implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(ManualTriageRunner.class);
    
    private final TriageService triageService;
    
    public ManualTriageRunner(TriageService triageService) {
        this.triageService = triageService;
    }
    
    @Override
    public void run(String... args) {
        logger.info("Running triage on startup...");
        int limit = 10; // Process only 10 most recent issues
        int exitCode = triageService.processAllPendingIssues(limit);
        logger.info("Triage completed with exit code: {}", exitCode);
    }
}
