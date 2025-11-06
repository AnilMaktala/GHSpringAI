package com.example.triage.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public class TriageResult {
    
    private Long issueNumber;
    private String issueTitle;
    private String issueUrl;
    private IssueCategory category;
    private Integer confidence;
    private String reasoning;
    private String responseSuggestion;
    private java.util.List<String> suggestedLabels;
    private String reproducibility; // "Easy", "Moderate", "Difficult", "Unknown", or null for non-applicable
    private String reproducibilityNotes;
    private boolean isDuplicate;
    private java.util.List<Integer> duplicateOf; // Issue numbers of potential duplicates
    private String duplicateReasoning;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime processedAt;
    
    private boolean flaggedForManualReview;
    
    public TriageResult() {
        this.processedAt = LocalDateTime.now();
    }
    
    public Long getIssueNumber() {
        return issueNumber;
    }
    
    public void setIssueNumber(Long issueNumber) {
        this.issueNumber = issueNumber;
    }
    
    public String getIssueTitle() {
        return issueTitle;
    }
    
    public void setIssueTitle(String issueTitle) {
        this.issueTitle = issueTitle;
    }
    
    public String getIssueUrl() {
        return issueUrl;
    }
    
    public void setIssueUrl(String issueUrl) {
        this.issueUrl = issueUrl;
    }
    
    public IssueCategory getCategory() {
        return category;
    }
    
    public void setCategory(IssueCategory category) {
        this.category = category;
    }
    
    public Integer getConfidence() {
        return confidence;
    }
    
    public void setConfidence(Integer confidence) {
        this.confidence = confidence;
        this.flaggedForManualReview = confidence != null && confidence < 70;
    }
    
    public String getReasoning() {
        return reasoning;
    }
    
    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
    
    public String getResponseSuggestion() {
        return responseSuggestion;
    }
    
    public void setResponseSuggestion(String responseSuggestion) {
        this.responseSuggestion = responseSuggestion;
    }
    
    // Alias for frontend compatibility
    public String getSuggestedResponse() {
        return responseSuggestion;
    }
    
    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
    
    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
    
    public boolean isFlaggedForManualReview() {
        return flaggedForManualReview;
    }
    
    public void setFlaggedForManualReview(boolean flaggedForManualReview) {
        this.flaggedForManualReview = flaggedForManualReview;
    }
    
    public java.util.List<String> getSuggestedLabels() {
        return suggestedLabels;
    }
    
    public void setSuggestedLabels(java.util.List<String> suggestedLabels) {
        this.suggestedLabels = suggestedLabels;
    }
    
    public String getReproducibility() {
        return reproducibility;
    }
    
    public void setReproducibility(String reproducibility) {
        this.reproducibility = reproducibility;
    }
    
    public String getReproducibilityNotes() {
        return reproducibilityNotes;
    }
    
    public void setReproducibilityNotes(String reproducibilityNotes) {
        this.reproducibilityNotes = reproducibilityNotes;
    }
    
    public boolean isDuplicate() {
        return isDuplicate;
    }
    
    public void setDuplicate(boolean duplicate) {
        isDuplicate = duplicate;
    }
    
    public java.util.List<Integer> getDuplicateOf() {
        return duplicateOf;
    }
    
    public void setDuplicateOf(java.util.List<Integer> duplicateOf) {
        this.duplicateOf = duplicateOf;
    }
    
    public String getDuplicateReasoning() {
        return duplicateReasoning;
    }
    
    public void setDuplicateReasoning(String duplicateReasoning) {
        this.duplicateReasoning = duplicateReasoning;
    }
}
