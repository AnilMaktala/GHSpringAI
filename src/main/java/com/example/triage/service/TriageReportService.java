package com.example.triage.service;

import com.example.triage.model.TriageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TriageReportService {
    
    private static final Logger logger = LoggerFactory.getLogger(TriageReportService.class);
    private static final String REPORT_FILE = "triage-report.md";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public void saveTriageResults(List<TriageResult> results) {
        try {
            Path reportPath = Paths.get(REPORT_FILE);
            
            // Check if file exists, if not create with header
            if (!Files.exists(reportPath)) {
                createNewReport(reportPath);
            }
            
            // Append new triage session
            appendTriageSession(reportPath, results);
            
            logger.info("Successfully saved {} triage results to {}", results.size(), REPORT_FILE);
        } catch (IOException e) {
            logger.error("Error saving triage results to markdown file: {}", e.getMessage(), e);
        }
    }
    
    private void createNewReport(Path reportPath) throws IOException {
        StringBuilder header = new StringBuilder();
        header.append("# GitHub Issue Triage Report\n\n");
        header.append("This file contains the history of all triaged issues.\n\n");
        header.append("---\n\n");
        
        Files.writeString(reportPath, header.toString(), StandardOpenOption.CREATE);
        logger.info("Created new triage report file: {}", REPORT_FILE);
    }
    
    private void appendTriageSession(Path reportPath, List<TriageResult> results) throws IOException {
        StringBuilder content = new StringBuilder();
        
        // Session header
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        content.append("## Triage Session - ").append(timestamp).append("\n\n");
        content.append("**Total Issues Triaged:** ").append(results.size()).append("\n\n");
        
        // Category summary
        long bugs = results.stream().filter(r -> r.getCategory().name().equals("BUG")).count();
        long features = results.stream().filter(r -> r.getCategory().name().equals("FEATURE_REQUEST")).count();
        long questions = results.stream().filter(r -> r.getCategory().name().equals("QUESTION")).count();
        long usability = results.stream().filter(r -> r.getCategory().name().equals("USABILITY")).count();
        
        content.append("**Category Distribution:**\n");
        content.append("- 🐛 Bugs: ").append(bugs).append("\n");
        content.append("- ✨ Features: ").append(features).append("\n");
        content.append("- ❓ Questions: ").append(questions).append("\n");
        content.append("- 🎨 Usability: ").append(usability).append("\n\n");
        
        // Individual issues
        content.append("### Triaged Issues\n\n");
        
        for (TriageResult result : results) {
            content.append("#### ");
            content.append(getCategoryEmoji(result.getCategory().name()));
            content.append(" Issue #").append(result.getIssueNumber());
            content.append(": ").append(result.getIssueTitle()).append("\n\n");
            
            content.append("- **Category:** ").append(formatCategory(result.getCategory().name())).append("\n");
            content.append("- **Confidence:** ").append(String.format("%d%%", result.getConfidence())).append("\n");
            content.append("- **URL:** [").append(result.getIssueUrl()).append("](").append(result.getIssueUrl()).append(")\n");
            
            if (result.getReasoning() != null && !result.getReasoning().isEmpty()) {
                content.append("- **Reasoning:** ").append(result.getReasoning()).append("\n");
            }
            
            if (result.isDuplicate() && result.getDuplicateOf() != null && !result.getDuplicateOf().isEmpty()) {
                content.append("- **⚠️ Possible Duplicate:** Issue #");
                content.append(result.getDuplicateOf().stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(", #")));
                content.append("\n");
                if (result.getDuplicateReasoning() != null && !result.getDuplicateReasoning().isEmpty()) {
                    content.append("  - ").append(result.getDuplicateReasoning()).append("\n");
                }
            }
            
            if (result.getSuggestedLabels() != null && !result.getSuggestedLabels().isEmpty()) {
                content.append("- **Suggested Labels:** ");
                content.append(String.join(", ", result.getSuggestedLabels()));
                content.append("\n");
            }
            
            if (result.getReproducibility() != null && !result.getReproducibility().isEmpty()) {
                content.append("- **Reproducibility:** ").append(result.getReproducibility()).append("\n");
                if (result.getReproducibilityNotes() != null && !result.getReproducibilityNotes().isEmpty()) {
                    content.append("  - ").append(result.getReproducibilityNotes()).append("\n");
                }
            }
            
            content.append("\n");
        }
        
        content.append("---\n\n");
        
        // Append to file
        Files.writeString(reportPath, content.toString(), StandardOpenOption.APPEND);
    }
    
    private String getCategoryEmoji(String category) {
        switch (category) {
            case "BUG":
                return "🐛";
            case "FEATURE_REQUEST":
                return "✨";
            case "QUESTION":
                return "❓";
            case "USABILITY":
                return "🎨";
            default:
                return "📝";
        }
    }
    
    private String formatCategory(String category) {
        switch (category) {
            case "BUG":
                return "Bug";
            case "FEATURE_REQUEST":
                return "Feature Request";
            case "QUESTION":
                return "Question";
            case "USABILITY":
                return "Usability";
            default:
                return category;
        }
    }
}
