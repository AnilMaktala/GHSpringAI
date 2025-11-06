package com.example.triage.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KnowledgeBase {
    
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private int totalIssuesAnalyzed;
    private Map<IssueCategory, List<IssueSummary>> categorizedIssues;
    private List<String> commonPatterns;
    private Map<String, Integer> labelFrequency;
    
    public KnowledgeBase() {
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
        this.categorizedIssues = new HashMap<>();
        this.commonPatterns = new ArrayList<>();
        this.labelFrequency = new HashMap<>();
        
        // Initialize categories
        for (IssueCategory category : IssueCategory.values()) {
            categorizedIssues.put(category, new ArrayList<>());
        }
    }
    
    public void addIssue(IssueSummary summary) {
        categorizedIssues.get(summary.getCategory()).add(summary);
        totalIssuesAnalyzed++;
        lastUpdated = LocalDateTime.now();
        
        // Update label frequency
        for (String label : summary.getLabels()) {
            labelFrequency.merge(label, 1, Integer::sum);
        }
    }
    
    public List<IssueSummary> getIssuesByCategory(IssueCategory category) {
        return categorizedIssues.getOrDefault(category, new ArrayList<>());
    }
    
    public String generateContextSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Knowledge Base Summary:\n");
        summary.append("Total Issues Analyzed: ").append(totalIssuesAnalyzed).append("\n\n");
        
        summary.append("Issue Distribution by Category:\n");
        for (IssueCategory category : IssueCategory.values()) {
            int count = categorizedIssues.get(category).size();
            if (count > 0) {
                summary.append("- ").append(category).append(": ").append(count).append(" issues\n");
            }
        }
        
        summary.append("\nCommon Patterns and Team Responses:\n");
        for (Map.Entry<IssueCategory, List<IssueSummary>> entry : categorizedIssues.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                summary.append("\n").append(entry.getKey()).append(" Examples:\n");
                entry.getValue().stream()
                    .limit(3)
                    .forEach(issue -> {
                        summary.append("  - #").append(issue.getNumber())
                            .append(": ").append(issue.getTitle()).append("\n");
                        if (issue.getOrgMemberComments() != null && !issue.getOrgMemberComments().isEmpty()) {
                            summary.append("    Team Response: ")
                                .append(issue.getOrgMemberComments().substring(0, 
                                    Math.min(issue.getOrgMemberComments().length(), 200)))
                                .append("...\n");
                        }
                    });
            }
        }
        
        return summary.toString();
    }
    
    // Getters and setters
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
    
    public int getTotalIssuesAnalyzed() {
        return totalIssuesAnalyzed;
    }
    
    public Map<IssueCategory, List<IssueSummary>> getCategorizedIssues() {
        return categorizedIssues;
    }
    
    public List<String> getCommonPatterns() {
        return commonPatterns;
    }
    
    public void setCommonPatterns(List<String> commonPatterns) {
        this.commonPatterns = commonPatterns;
    }
    
    public Map<String, Integer> getLabelFrequency() {
        return labelFrequency;
    }
    
    public static class IssueSummary {
        private int number;
        private String title;
        private IssueCategory category;
        private List<String> labels;
        private String keyTerms;
        private String orgMemberComments;
        
        public IssueSummary(int number, String title, IssueCategory category, List<String> labels) {
            this.number = number;
            this.title = title;
            this.category = category;
            this.labels = labels;
        }
        
        public IssueSummary(int number, String title, IssueCategory category, List<String> labels, String orgMemberComments) {
            this.number = number;
            this.title = title;
            this.category = category;
            this.labels = labels;
            this.orgMemberComments = orgMemberComments;
        }
        
        public int getNumber() {
            return number;
        }
        
        public String getTitle() {
            return title;
        }
        
        public IssueCategory getCategory() {
            return category;
        }
        
        public List<String> getLabels() {
            return labels;
        }
        
        public String getKeyTerms() {
            return keyTerms;
        }
        
        public void setKeyTerms(String keyTerms) {
            this.keyTerms = keyTerms;
        }
        
        public String getOrgMemberComments() {
            return orgMemberComments;
        }
        
        public void setOrgMemberComments(String orgMemberComments) {
            this.orgMemberComments = orgMemberComments;
        }
    }
}
