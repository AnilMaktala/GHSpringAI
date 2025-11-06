package com.example.triage.service;

import com.example.triage.client.GitHubClient;
import com.example.triage.model.GitHubIssue;
import com.example.triage.model.IssueCategory;
import com.example.triage.model.KnowledgeBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseService.class);
    private static final String KB_FILE_PATH = "./knowledge-base.json";
    
    private final GitHubClient gitHubClient;
    private final AIClassificationService classificationService;
    private final ObjectMapper objectMapper;
    private KnowledgeBase knowledgeBase;
    
    public KnowledgeBaseService(GitHubClient gitHubClient, 
                               AIClassificationService classificationService,
                               ObjectMapper objectMapper) {
        this.gitHubClient = gitHubClient;
        this.classificationService = classificationService;
        this.objectMapper = objectMapper;
        this.knowledgeBase = loadKnowledgeBase();
    }
    
    public KnowledgeBase buildKnowledgeBase(int maxIssues) {
        logger.info("Building knowledge base from up to {} triaged issues (without pending-triage label)", maxIssues);
        
        KnowledgeBase kb = new KnowledgeBase();
        
        try {
            // Fetch all triaged issues (both open and closed, excluding pending-triage)
            List<GitHubIssue> triagedIssues = gitHubClient.fetchTriagedIssues(maxIssues);
            logger.info("Fetched {} triaged issues for knowledge base", triagedIssues.size());
            
            int processed = 0;
            for (GitHubIssue issue : triagedIssues) {
                try {
                    // Determine category from labels
                    IssueCategory category = inferCategoryFromLabels(issue);
                    
                    if (category != null) {
                        List<String> labelNames = issue.getLabels().stream()
                            .map(label -> label.getName())
                            .collect(Collectors.toList());
                        
                        // Fetch org member comments for this issue
                        String orgComments = gitHubClient.fetchOrgMemberComments(issue.getNumber(), 10);
                        
                        KnowledgeBase.IssueSummary summary = new KnowledgeBase.IssueSummary(
                            issue.getNumber().intValue(),
                            issue.getTitle(),
                            category,
                            labelNames,
                            orgComments
                        );
                        
                        kb.addIssue(summary);
                        processed++;
                        
                        if (processed % 50 == 0) {
                            logger.info("Processed {} issues for knowledge base", processed);
                        }
                    }
                    
                    // Rate limiting
                    if (processed % 10 == 0) {
                        Thread.sleep(1000);
                    }
                    
                } catch (Exception e) {
                    logger.warn("Error processing issue #{}: {}", issue.getNumber(), e.getMessage());
                }
            }
            
            logger.info("Knowledge base built with {} issues", kb.getTotalIssuesAnalyzed());
            
            // Save knowledge base
            saveKnowledgeBase(kb);
            this.knowledgeBase = kb;
            
            return kb;
            
        } catch (Exception e) {
            logger.error("Error building knowledge base: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build knowledge base", e);
        }
    }
    
    private IssueCategory inferCategoryFromLabels(GitHubIssue issue) {
        List<String> labelNames = issue.getLabels().stream()
            .map(label -> label.getName().toLowerCase())
            .collect(Collectors.toList());
        
        // Check for explicit category labels
        if (labelNames.contains("bug") || labelNames.contains("defect")) {
            return IssueCategory.BUG;
        }
        if (labelNames.contains("feature") || labelNames.contains("enhancement") || 
            labelNames.contains("feature-request")) {
            return IssueCategory.FEATURE_REQUEST;
        }
        if (labelNames.contains("question") || labelNames.contains("help wanted")) {
            return IssueCategory.QUESTION;
        }
        if (labelNames.contains("usability") || labelNames.contains("ux") || labelNames.contains("ui")) {
            return IssueCategory.USABILITY;
        }
        
        return null; // Unknown category
    }
    
    public KnowledgeBase getKnowledgeBase() {
        if (knowledgeBase == null || knowledgeBase.getTotalIssuesAnalyzed() == 0) {
            logger.info("Knowledge base is empty, loading from file");
            knowledgeBase = loadKnowledgeBase();
        }
        return knowledgeBase;
    }
    
    public String getKnowledgeBaseContext() {
        KnowledgeBase kb = getKnowledgeBase();
        if (kb == null || kb.getTotalIssuesAnalyzed() == 0) {
            return "No knowledge base available. Build one first by analyzing closed issues.";
        }
        return kb.generateContextSummary();
    }
    
    private void saveKnowledgeBase(KnowledgeBase kb) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File(KB_FILE_PATH), kb);
            logger.info("Knowledge base saved to {}", KB_FILE_PATH);
        } catch (IOException e) {
            logger.error("Failed to save knowledge base: {}", e.getMessage());
        }
    }
    
    private KnowledgeBase loadKnowledgeBase() {
        File kbFile = new File(KB_FILE_PATH);
        if (kbFile.exists()) {
            try {
                KnowledgeBase kb = objectMapper.readValue(kbFile, KnowledgeBase.class);
                logger.info("Loaded knowledge base with {} issues", kb.getTotalIssuesAnalyzed());
                return kb;
            } catch (IOException e) {
                logger.warn("Failed to load knowledge base: {}", e.getMessage());
            }
        }
        logger.info("No existing knowledge base found, creating new one");
        return new KnowledgeBase();
    }
    
    public boolean hasKnowledgeBase() {
        return knowledgeBase != null && knowledgeBase.getTotalIssuesAnalyzed() > 0;
    }
}
