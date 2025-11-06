package com.example.triage.service;

import com.example.triage.client.GitHubApiException;
import com.example.triage.client.GitHubClient;
import com.example.triage.model.GitHubIssue;
import com.example.triage.model.IssueCategory;
import com.example.triage.model.TriageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TriageService {
    
    private static final Logger logger = LoggerFactory.getLogger(TriageService.class);
    
    private final GitHubClient gitHubClient;
    private final AIClassificationService aiClassificationService;
    private final ResultPersistenceService resultPersistenceService;
    
    public TriageService(
            GitHubClient gitHubClient,
            AIClassificationService aiClassificationService,
            ResultPersistenceService resultPersistenceService) {
        this.gitHubClient = gitHubClient;
        this.aiClassificationService = aiClassificationService;
        this.resultPersistenceService = resultPersistenceService;
    }
    
    public int processAllPendingIssues() {
        return processAllPendingIssues(100); // Default limit
    }
    
    public int processAllPendingIssues(int limit) {
        logger.info("Starting triage process for pending issues (limit: {})", limit);
        
        try {
            // Fetch issues
            List<GitHubIssue> issues = gitHubClient.fetchPendingTriageIssues(limit);
            
            if (issues.isEmpty()) {
                logger.info("No pending-triage issues found. Triage process completed successfully.");
                return 0;
            }
            
            // Process each issue with delay to avoid rate limiting
            List<TriageResult> results = new ArrayList<>();
            int failedCount = 0;
            
            for (int i = 0; i < issues.size(); i++) {
                GitHubIssue issue = issues.get(i);
                try {
                    TriageResult result = processIssue(issue);
                    results.add(result);
                    
                    // Add delay between requests to avoid rate limiting (except for last issue)
                    if (i < issues.size() - 1) {
                        logger.info("Waiting 10 seconds before processing next issue to avoid rate limiting...");
                        Thread.sleep(10000); // 10 second delay between requests
                    }
                } catch (InterruptedException e) {
                    logger.warn("Sleep interrupted: {}", e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    logger.error("Failed to process issue #{}: {}", issue.getNumber(), e.getMessage());
                    failedCount++;
                }
            }
            
            // Persist results
            if (!results.isEmpty()) {
                resultPersistenceService.saveResults(results);
                logSummary(results, failedCount);
            }
            
            logger.info("Triage process completed. Processed: {}, Failed: {}", 
                results.size(), failedCount);
            
            return 0; // Success
            
        } catch (GitHubApiException e) {
            logger.error("GitHub API error during triage: {}", e.getMessage());
            return e.getExitCode();
        } catch (Exception e) {
            logger.error("Unexpected error during triage process: {}", e.getMessage(), e);
            return 1;
        }
    }
    
    public TriageResult processIssue(GitHubIssue issue) {
        logger.debug("Processing issue #{}: {}", issue.getNumber(), issue.getTitle());
        return aiClassificationService.classifyIssue(issue);
    }
    
    private void logSummary(List<TriageResult> results, int failedCount) {
        Map<IssueCategory, Long> categoryCounts = results.stream()
            .collect(Collectors.groupingBy(TriageResult::getCategory, Collectors.counting()));
        
        logger.info("=== Triage Summary ===");
        logger.info("Total issues processed: {}", results.size());
        logger.info("Failed issues: {}", failedCount);
        
        for (IssueCategory category : IssueCategory.values()) {
            long count = categoryCounts.getOrDefault(category, 0L);
            logger.info("{}: {}", category.getDisplayName(), count);
        }
        
        long flaggedCount = results.stream()
            .filter(TriageResult::isFlaggedForManualReview)
            .count();
        logger.info("Flagged for manual review: {}", flaggedCount);
        logger.info("======================");
    }
}
