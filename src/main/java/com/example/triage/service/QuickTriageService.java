package com.example.triage.service;

import com.example.triage.client.GitHubClient;
import com.example.triage.model.GitHubIssue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuickTriageService {
    
    private static final Logger logger = LoggerFactory.getLogger(QuickTriageService.class);
    private static final String QUICK_TRIAGE_FILE = "quick-triage.md";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final GitHubClient gitHubClient;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    
    public QuickTriageService(GitHubClient gitHubClient, ChatClient.Builder chatClientBuilder) {
        this.gitHubClient = gitHubClient;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }
    
    public List<QuickTriageResult> quickTriageAllPendingIssues(int limit) {
        logger.info("Starting quick triage for {} pending issues", limit);
        
        try {
            // Fetch pending issues
            List<GitHubIssue> issues = gitHubClient.fetchPendingTriageIssues(limit);
            
            if (issues.isEmpty()) {
                logger.info("No pending-triage issues found");
                return new ArrayList<>();
            }
            
            // Quick categorize each issue
            List<QuickTriageResult> results = new ArrayList<>();
            for (GitHubIssue issue : issues) {
                try {
                    QuickTriageResult result = quickCategorizeIssue(issue);
                    results.add(result);
                    logger.info("Quick triaged issue #{}: {}", issue.getNumber(), result.getCategory());
                } catch (Exception e) {
                    logger.error("Error quick triaging issue #{}: {}", issue.getNumber(), e.getMessage());
                }
            }
            
            // Save to markdown file
            if (!results.isEmpty()) {
                saveQuickTriageResults(results);
            }
            
            return results;
            
        } catch (Exception e) {
            logger.error("Error in quick triage process: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    private QuickTriageResult quickCategorizeIssue(GitHubIssue issue) {
        String prompt = """
            You are a GitHub issue triage assistant. Based ONLY on the title and description below, 
            quickly categorize this issue into ONE of these categories:
            - BUG: Something is broken or not working as expected
            - FEATURE: Request for new functionality or enhancement
            - QUESTION: User asking how to do something or seeking clarification
            - USABILITY: UI/UX improvement, confusing interface, or user experience issue
            
            Title: {title}
            Description: {description}
            
            Respond with ONLY the category name (BUG, FEATURE, QUESTION, or USABILITY) and a brief one-sentence reason.
            Format: CATEGORY: reason
            Example: BUG: Application crashes when clicking the submit button
            """;
        
        Map<String, Object> model = new HashMap<>();
        model.put("title", issue.getTitle());
        model.put("description", issue.getBody() != null ? issue.getBody() : "No description provided");
        
        PromptTemplate promptTemplate = new PromptTemplate(prompt, model);
        Prompt aiPrompt = promptTemplate.create();
        
        String response = chatClient.prompt(aiPrompt).call().content();
        
        // Parse response
        QuickTriageResult result = new QuickTriageResult();
        result.setIssueNumber(issue.getNumber());
        result.setIssueTitle(issue.getTitle());
        result.setIssueUrl(issue.getUrl());
        result.setAuthor(issue.getUser().getLogin());
        result.setCreatedAt(issue.getCreatedAt().toString());
        
        // Extract category and reason
        String[] parts = response.split(":", 2);
        if (parts.length == 2) {
            String category = parts[0].trim().toUpperCase();
            String reason = parts[1].trim();
            
            // Validate category
            if (category.equals("BUG") || category.equals("FEATURE") || 
                category.equals("QUESTION") || category.equals("USABILITY")) {
                result.setCategory(category);
                result.setReason(reason);
            } else {
                // Default to QUESTION if invalid
                result.setCategory("QUESTION");
                result.setReason("Unable to determine category from title and description");
            }
        } else {
            result.setCategory("QUESTION");
            result.setReason("Unable to parse AI response");
        }
        
        return result;
    }
    
    private void saveQuickTriageResults(List<QuickTriageResult> results) {
        try {
            Path filePath = Paths.get(QUICK_TRIAGE_FILE);
            
            // Check if file exists, if not create with header
            if (!Files.exists(filePath)) {
                createNewQuickTriageFile(filePath);
            }
            
            // Append new session
            appendQuickTriageSession(filePath, results);
            
            logger.info("Successfully saved {} quick triage results to {}", results.size(), QUICK_TRIAGE_FILE);
        } catch (IOException e) {
            logger.error("Error saving quick triage results: {}", e.getMessage(), e);
        }
    }
    
    private void createNewQuickTriageFile(Path filePath) throws IOException {
        StringBuilder header = new StringBuilder();
        header.append("# Quick Triage Results\n\n");
        header.append("This file contains quick categorization of pending-triage issues based on title and description only.\n\n");
        header.append("---\n\n");
        
        Files.writeString(filePath, header.toString(), StandardOpenOption.CREATE);
    }
    
    private void appendQuickTriageSession(Path filePath, List<QuickTriageResult> results) throws IOException {
        StringBuilder content = new StringBuilder();
        
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        content.append("## Quick Triage Session - ").append(timestamp).append("\n\n");
        content.append("**Total Issues:** ").append(results.size()).append("\n\n");
        
        // Category summary
        long bugs = results.stream().filter(r -> r.getCategory().equals("BUG")).count();
        long features = results.stream().filter(r -> r.getCategory().equals("FEATURE")).count();
        long questions = results.stream().filter(r -> r.getCategory().equals("QUESTION")).count();
        long usability = results.stream().filter(r -> r.getCategory().equals("USABILITY")).count();
        
        content.append("**Category Distribution:**\n");
        content.append("- 🐛 Bugs: ").append(bugs).append("\n");
        content.append("- ✨ Features: ").append(features).append("\n");
        content.append("- ❓ Questions: ").append(questions).append("\n");
        content.append("- 🎨 Usability: ").append(usability).append("\n\n");
        
        // Individual issues
        content.append("### Issues\n\n");
        
        for (QuickTriageResult result : results) {
            content.append("#### ");
            content.append(getCategoryEmoji(result.getCategory()));
            content.append(" #").append(result.getIssueNumber());
            content.append(": ").append(result.getIssueTitle()).append("\n\n");
            
            content.append("- **Category:** ").append(result.getCategory()).append("\n");
            content.append("- **Reason:** ").append(result.getReason()).append("\n");
            content.append("- **Author:** ").append(result.getAuthor()).append("\n");
            content.append("- **URL:** [").append(result.getIssueUrl()).append("](").append(result.getIssueUrl()).append(")\n\n");
        }
        
        content.append("---\n\n");
        
        Files.writeString(filePath, content.toString(), StandardOpenOption.APPEND);
    }
    
    private String getCategoryEmoji(String category) {
        switch (category) {
            case "BUG":
                return "🐛";
            case "FEATURE":
                return "✨";
            case "QUESTION":
                return "❓";
            case "USABILITY":
                return "🎨";
            default:
                return "📝";
        }
    }
    
    public List<QuickTriageResult> loadQuickTriageResults() {
        try {
            Path filePath = Paths.get(QUICK_TRIAGE_FILE);
            
            if (!Files.exists(filePath)) {
                return new ArrayList<>();
            }
            
            // Parse the markdown file to extract results
            String content = Files.readString(filePath);
            return parseQuickTriageMarkdown(content);
            
        } catch (IOException e) {
            logger.error("Error loading quick triage results: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    private List<QuickTriageResult> parseQuickTriageMarkdown(String content) {
        List<QuickTriageResult> results = new ArrayList<>();
        
        // Find the last session (most recent)
        String[] sessions = content.split("## Quick Triage Session");
        if (sessions.length < 2) {
            return results;
        }
        
        String lastSession = sessions[sessions.length - 1];
        String[] lines = lastSession.split("\n");
        
        QuickTriageResult currentResult = null;
        
        for (String line : lines) {
            line = line.trim();
            
            if (line.startsWith("#### ")) {
                // New issue
                if (currentResult != null) {
                    results.add(currentResult);
                }
                currentResult = new QuickTriageResult();
                
                // Extract issue number and title
                String issueInfo = line.substring(4).trim();
                // Remove emoji
                issueInfo = issueInfo.replaceAll("[🐛✨❓🎨📝]", "").trim();
                
                if (issueInfo.startsWith("#")) {
                    int colonIndex = issueInfo.indexOf(":");
                    if (colonIndex > 0) {
                        String numberStr = issueInfo.substring(1, colonIndex).trim();
                        try {
                            currentResult.setIssueNumber(Long.parseLong(numberStr));
                        } catch (NumberFormatException e) {
                            // Skip
                        }
                        currentResult.setIssueTitle(issueInfo.substring(colonIndex + 1).trim());
                    }
                }
            } else if (line.startsWith("- **Category:**") && currentResult != null) {
                currentResult.setCategory(line.substring(15).trim());
            } else if (line.startsWith("- **Reason:**") && currentResult != null) {
                currentResult.setReason(line.substring(13).trim());
            } else if (line.startsWith("- **Author:**") && currentResult != null) {
                currentResult.setAuthor(line.substring(13).trim());
            } else if (line.startsWith("- **URL:**") && currentResult != null) {
                // Extract URL from markdown link
                int startIdx = line.indexOf("](");
                int endIdx = line.indexOf(")", startIdx);
                if (startIdx > 0 && endIdx > startIdx) {
                    currentResult.setIssueUrl(line.substring(startIdx + 2, endIdx));
                }
            }
        }
        
        // Add last result
        if (currentResult != null && currentResult.getIssueNumber() != null) {
            results.add(currentResult);
        }
        
        return results;
    }
    
    public static class QuickTriageResult {
        private Long issueNumber;
        private String issueTitle;
        private String issueUrl;
        private String category;
        private String reason;
        private String author;
        private String createdAt;
        
        // Getters and setters
        public Long getIssueNumber() { return issueNumber; }
        public void setIssueNumber(Long issueNumber) { this.issueNumber = issueNumber; }
        
        public String getIssueTitle() { return issueTitle; }
        public void setIssueTitle(String issueTitle) { this.issueTitle = issueTitle; }
        
        public String getIssueUrl() { return issueUrl; }
        public void setIssueUrl(String issueUrl) { this.issueUrl = issueUrl; }
        
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }
}
