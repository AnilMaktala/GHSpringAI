package com.example.triage.controller;

import com.example.triage.model.GitHubIssue;
import com.example.triage.model.IssueCategory;
import com.example.triage.model.KnowledgeBase;
import com.example.triage.model.TriageResult;
import com.example.triage.service.AIClassificationService;
import com.example.triage.service.KnowledgeBaseService;
import com.example.triage.service.TriageReportService;
import com.example.triage.service.QuickTriageService;
import com.example.triage.client.GitHubClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/triage")
@CrossOrigin(origins = "*")
public class TriageController {
    
    private static final Logger logger = LoggerFactory.getLogger(TriageController.class);
    
    private final GitHubClient gitHubClient;
    private final AIClassificationService classificationService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final TriageReportService triageReportService;
    private final QuickTriageService quickTriageService;
    
    public TriageController(GitHubClient gitHubClient, 
                           AIClassificationService classificationService,
                           KnowledgeBaseService knowledgeBaseService,
                           TriageReportService triageReportService,
                           QuickTriageService quickTriageService) {
        this.gitHubClient = gitHubClient;
        this.classificationService = classificationService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.triageReportService = triageReportService;
        this.quickTriageService = quickTriageService;
        
        // Set knowledge base service and github client in classification service
        classificationService.setKnowledgeBaseService(knowledgeBaseService);
        classificationService.setGitHubClient(gitHubClient);
    }
    
    @GetMapping("/issues")
    public ResponseEntity<Map<String, Object>> getPendingIssues(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        logger.info("Fetching {} pending-triage issues with offset {}", limit, offset);
        
        // Fetch all pending issues to get total count (set high limit to get all)
        List<GitHubIssue> allIssues = gitHubClient.fetchPendingTriageIssues(10000);
        int totalCount = allIssues.size();
        
        // Get paginated subset
        int endIndex = Math.min(offset + limit, totalCount);
        List<GitHubIssue> paginatedIssues = allIssues.subList(offset, endIndex);
        
        Map<String, Object> response = new HashMap<>();
        response.put("issues", paginatedIssues);
        response.put("total", totalCount);
        response.put("offset", offset);
        response.put("limit", limit);
        response.put("hasMore", endIndex < totalCount);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        logger.info("Fetching triage statistics");
        try {
            // Fetch all pending issues (set high limit to get all)
            List<GitHubIssue> allIssues = gitHubClient.fetchPendingTriageIssues(10000);
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalPending", allIssues.size());
            
            // Count by existing labels (if any issues have been pre-triaged)
            Map<String, Integer> categoryCount = new HashMap<>();
            categoryCount.put("bug", 0);
            categoryCount.put("feature", 0);
            categoryCount.put("question", 0);
            categoryCount.put("usability", 0);
            categoryCount.put("unlabeled", 0);
            
            for (GitHubIssue issue : allIssues) {
                boolean categorized = false;
                for (GitHubIssue.Label label : issue.getLabels()) {
                    String labelName = label.getName().toLowerCase();
                    if (labelName.contains("bug") || labelName.equals("defect")) {
                        categoryCount.put("bug", categoryCount.get("bug") + 1);
                        categorized = true;
                        break;
                    } else if (labelName.contains("feature") || labelName.contains("enhancement")) {
                        categoryCount.put("feature", categoryCount.get("feature") + 1);
                        categorized = true;
                        break;
                    } else if (labelName.contains("question")) {
                        categoryCount.put("question", categoryCount.get("question") + 1);
                        categorized = true;
                        break;
                    } else if (labelName.contains("usability") || labelName.contains("ux")) {
                        categoryCount.put("usability", categoryCount.get("usability") + 1);
                        categorized = true;
                        break;
                    }
                }
                if (!categorized) {
                    categoryCount.put("unlabeled", categoryCount.get("unlabeled") + 1);
                }
            }
            
            stats.put("byCategory", categoryCount);
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error fetching statistics: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/classify/{issueNumber}")
    public ResponseEntity<TriageResult> classifyIssue(
            @PathVariable int issueNumber,
            @RequestParam(required = false) String model) {
        logger.info("Classifying issue #{} with model: {}", issueNumber, model);
        try {
            // Fetch the specific issue
            List<GitHubIssue> issues = gitHubClient.fetchPendingTriageIssues(100);
            GitHubIssue issue = issues.stream()
                    .filter(i -> i.getNumber() == issueNumber)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Issue not found: " + issueNumber));
            
            // Classify the issue with optional model override
            TriageResult result = classificationService.classifyIssue(issue, model);
            logger.info("Issue #{} classified as {} with confidence {}", 
                    issueNumber, result.getCategory(), result.getConfidence());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error classifying issue #{}: {}", issueNumber, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/knowledge-base/build")
    public ResponseEntity<Map<String, Object>> buildKnowledgeBase(
            @RequestParam(defaultValue = "2000") int maxIssues) {
        logger.info("Building knowledge base from {} issues", maxIssues);
        try {
            KnowledgeBase kb = knowledgeBaseService.buildKnowledgeBase(maxIssues);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalIssuesAnalyzed", kb.getTotalIssuesAnalyzed());
            response.put("createdAt", kb.getCreatedAt());
            response.put("message", "Knowledge base built successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error building knowledge base: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    @GetMapping("/knowledge-base/status")
    public ResponseEntity<Map<String, Object>> getKnowledgeBaseStatus() {
        try {
            KnowledgeBase kb = knowledgeBaseService.getKnowledgeBase();
            
            Map<String, Object> status = new HashMap<>();
            status.put("exists", knowledgeBaseService.hasKnowledgeBase());
            status.put("totalIssuesAnalyzed", kb.getTotalIssuesAnalyzed());
            status.put("lastUpdated", kb.getLastUpdated());
            status.put("createdAt", kb.getCreatedAt());
            
            Map<String, Integer> distribution = new HashMap<>();
            for (IssueCategory category : IssueCategory.values()) {
                distribution.put(category.name(), kb.getIssuesByCategory(category).size());
            }
            status.put("categoryDistribution", distribution);
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Error getting knowledge base status: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/categorize-all")
    public ResponseEntity<Map<String, Object>> categorizeAllPendingIssues() {
        logger.info("Starting bulk categorization of first 10 pending-triage issues");
        try {
            // Fetch only first 10 pending issues
            List<GitHubIssue> issues = gitHubClient.fetchPendingTriageIssues(10);
            int totalIssues = issues.size();
            
            if (totalIssues == 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("results", List.of());
                response.put("totalIssues", 0);
                response.put("message", "No pending-triage issues found");
                return ResponseEntity.ok(response);
            }
            
            // Categorize each issue
            List<TriageResult> results = new java.util.ArrayList<>();
            for (GitHubIssue issue : issues) {
                try {
                    TriageResult result = classificationService.classifyIssue(issue, null);
                    results.add(result);
                    logger.info("Categorized issue #{} as {}", issue.getNumber(), result.getCategory());
                } catch (Exception e) {
                    logger.error("Error categorizing issue #{}: {}", issue.getNumber(), e.getMessage());
                }
            }
            
            // Save results to markdown file
            if (!results.isEmpty()) {
                triageReportService.saveTriageResults(results);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            response.put("totalIssues", totalIssues);
            response.put("message", String.format("Successfully categorized %d issues and saved to triage-report.md", results.size()));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error in bulk categorization: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    @PostMapping("/update-labels/{issueNumber}")
    public ResponseEntity<Map<String, Object>> updateIssueLabels(
            @PathVariable int issueNumber,
            @RequestParam String category) {
        logger.info("Updating labels for issue #{} with category {}", issueNumber, category);
        try {
            // Map category to label
            String categoryLabel = getCategoryLabel(category);
            
            // Labels to add and remove
            List<String> labelsToAdd = new java.util.ArrayList<>();
            labelsToAdd.add(categoryLabel);
            
            List<String> labelsToRemove = new java.util.ArrayList<>();
            labelsToRemove.add("pending-triage");
            
            // Update labels on GitHub
            gitHubClient.updateIssueLabels(issueNumber, labelsToAdd, labelsToRemove);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("issueNumber", issueNumber);
            response.put("addedLabels", labelsToAdd);
            response.put("removedLabels", labelsToRemove);
            response.put("message", "Labels updated successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating labels for issue #{}: {}", issueNumber, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    private String getCategoryLabel(String category) {
        switch (category.toUpperCase()) {
            case "BUG":
                return "type: bug";
            case "FEATURE_REQUEST":
                return "type: feature";
            case "QUESTION":
                return "type: question";
            case "USABILITY":
                return "type: usability";
            default:
                return "type: other";
        }
    }
    
    @PostMapping("/quick-triage")
    public ResponseEntity<Map<String, Object>> quickTriageIssues(
            @RequestParam(defaultValue = "10") int limit) {
        logger.info("Starting quick triage for {} issues", limit);
        try {
            List<QuickTriageService.QuickTriageResult> results = 
                quickTriageService.quickTriageAllPendingIssues(limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            response.put("totalIssues", results.size());
            response.put("message", String.format("Successfully quick triaged %d issues and saved to quick-triage.md", results.size()));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error in quick triage: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    @GetMapping("/quick-triage-results")
    public ResponseEntity<Map<String, Object>> getQuickTriageResults() {
        logger.info("Fetching quick triage results from file");
        try {
            List<QuickTriageService.QuickTriageResult> results = 
                quickTriageService.loadQuickTriageResults();
            
            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            response.put("totalIssues", results.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching quick triage results: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/categorized-issues")
    public ResponseEntity<Map<String, Object>> getCategorizedIssues(
            @RequestParam(defaultValue = "25") int pageSize,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String category) {
        logger.info("Fetching categorized issues (page: {}, size: {}, category: {})", page, pageSize, category);
        try {
            // This endpoint would return previously categorized issues
            // For now, we'll return an empty response as categorization happens on-demand
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Use /categorize-all endpoint to categorize issues");
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("category", category);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching categorized issues: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/search-issue/{issueId}")
    public ResponseEntity<Map<String, Object>> searchAndTriageIssue(@PathVariable int issueId) {
        logger.info("Searching and triaging issue #{}", issueId);
        
        // Validate issue ID
        if (issueId <= 0) {
            logger.warn("Invalid issue ID: {}", issueId);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Issue ID must be a positive integer");
            error.put("errorCode", "INVALID_ISSUE_ID");
            return ResponseEntity.badRequest().body(error);
        }
        
        try {
            // Fetch the specific issue
            GitHubIssue issue = gitHubClient.fetchIssueById(issueId);
            
            if (issue == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Issue #" + issueId + " not found");
                error.put("errorCode", "ISSUE_NOT_FOUND");
                return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body(error);
            }
            
            // Classify the issue
            TriageResult triageResult = classificationService.classifyIssue(issue, null);
            logger.info("Issue #{} classified as {} with confidence {}", 
                    issueId, triageResult.getCategory(), triageResult.getConfidence());
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            
            // Issue details
            Map<String, Object> issueDetails = new HashMap<>();
            issueDetails.put("number", issue.getNumber());
            issueDetails.put("title", issue.getTitle());
            issueDetails.put("url", issue.getUrl());
            issueDetails.put("author", issue.getAuthor());
            issueDetails.put("createdAt", issue.getCreatedAt());
            issueDetails.put("body", issue.getBody());
            response.put("issue", issueDetails);
            
            // Triage result
            Map<String, Object> triageDetails = new HashMap<>();
            triageDetails.put("category", triageResult.getCategory());
            triageDetails.put("confidence", triageResult.getConfidence());
            triageDetails.put("reasoning", triageResult.getReasoning());
            triageDetails.put("suggestedLabels", triageResult.getSuggestedLabels());
            triageDetails.put("responseSuggestion", triageResult.getResponseSuggestion());
            triageDetails.put("reproducibility", triageResult.getReproducibility());
            triageDetails.put("reproducibilityNotes", triageResult.getReproducibilityNotes());
            triageDetails.put("isDuplicate", triageResult.isDuplicate());
            triageDetails.put("duplicateOf", triageResult.getDuplicateOf());
            triageDetails.put("duplicateReasoning", triageResult.getDuplicateReasoning());
            response.put("triageResult", triageDetails);
            
            return ResponseEntity.ok(response);
            
        } catch (com.example.triage.client.GitHubApiException e) {
            logger.error("GitHub API error for issue #{}: {}", issueId, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            
            // Map error codes
            if (e.getExitCode() == 4) { // Not found
                error.put("error", "Issue #" + issueId + " not found");
                error.put("errorCode", "ISSUE_NOT_FOUND");
                return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body(error);
            } else if (e.getExitCode() == 2) { // Rate limit
                error.put("error", e.getMessage());
                error.put("errorCode", "RATE_LIMIT_EXCEEDED");
                return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).body(error);
            } else if (e.getExitCode() == 3) { // Auth error
                error.put("error", "GitHub authentication failed");
                error.put("errorCode", "GITHUB_API_ERROR");
                return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(error);
            } else {
                error.put("error", "Failed to fetch issue from GitHub");
                error.put("errorCode", "GITHUB_API_ERROR");
                return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(error);
            }
        } catch (Exception e) {
            logger.error("Error searching and triaging issue #{}: {}", issueId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to classify issue");
            error.put("errorCode", "CLASSIFICATION_ERROR");
            return ResponseEntity.internalServerError().body(error);
        }
    }
}

