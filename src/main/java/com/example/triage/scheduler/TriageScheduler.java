package com.example.triage.scheduler;

import com.example.triage.service.TriageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class TriageScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(TriageScheduler.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final TriageService triageService;
    
    public TriageScheduler(TriageService triageService) {
        this.triageService = triageService;
    }
    
    @Scheduled(cron = "${triage.schedule}")
    public void executeTriage() {
        LocalDateTime startTime = LocalDateTime.now();
        logger.info("=== Scheduled triage execution started at {} ===", startTime.format(formatter));
        
        try {
            int exitCode = triageService.processAllPendingIssues();
            
            LocalDateTime endTime = LocalDateTime.now();
            logger.info("=== Scheduled triage execution completed at {} (exit code: {}) ===", 
                endTime.format(formatter), exitCode);
            
        } catch (Exception e) {
            LocalDateTime endTime = LocalDateTime.now();
            logger.error("=== Scheduled triage execution failed at {} ===", endTime.format(formatter), e);
        }
    }
}
