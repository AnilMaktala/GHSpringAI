package com.example.triage.service;

import com.example.triage.config.TriageConfiguration;
import com.example.triage.model.IssueCategory;
import com.example.triage.model.TriageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResultPersistenceService {
    
    private static final Logger logger = LoggerFactory.getLogger(ResultPersistenceService.class);
    
    private final TriageConfiguration config;
    private final ObjectMapper objectMapper;
    
    public ResultPersistenceService(TriageConfiguration config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    public void saveResults(List<TriageResult> results) {
        String outputPath = config.getOutputPath();
        
        try {
            Map<String, Object> output = formatAsJson(results);
            writeToFile(outputPath, output);
            logger.info("Successfully saved triage results to {}", outputPath);
            
        } catch (IOException e) {
            logger.error("Failed to write to {}: {}", outputPath, e.getMessage());
            
            // Try fallback location
            String fallbackPath = generateFallbackPath();
            try {
                Map<String, Object> output = formatAsJson(results);
                writeToFile(fallbackPath, output);
                logger.info("Successfully saved triage results to fallback location: {}", fallbackPath);
            } catch (IOException fallbackError) {
                logger.error("Failed to write to fallback location {}: {}", 
                    fallbackPath, fallbackError.getMessage());
                throw new RuntimeException("Failed to persist triage results", fallbackError);
            }
        }
    }
    
    private Map<String, Object> formatAsJson(List<TriageResult> results) {
        Map<String, Object> output = new HashMap<>();
        
        Map<String, Object> triageRun = new HashMap<>();
        triageRun.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        triageRun.put("totalIssues", results.size());
        triageRun.put("summary", generateSummary(results));
        
        output.put("triageRun", triageRun);
        output.put("results", results);
        
        return output;
    }
    
    private Map<String, Integer> generateSummary(List<TriageResult> results) {
        Map<String, Integer> summary = new HashMap<>();
        
        for (IssueCategory category : IssueCategory.values()) {
            long count = results.stream()
                .filter(r -> r.getCategory() == category)
                .count();
            summary.put(category.getDisplayName(), (int) count);
        }
        
        return summary;
    }
    
    private void writeToFile(String path, Map<String, Object> data) throws IOException {
        File file = new File(path);
        File parentDir = file.getParentFile();
        
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        objectMapper.writeValue(file, data);
    }
    
    private String generateFallbackPath() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return "./triage-results-" + timestamp + ".json";
    }
}
