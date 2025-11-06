package com.example.triage.service;

import com.example.triage.config.TriageConfiguration;
import com.example.triage.model.GitHubIssue;
import com.example.triage.model.IssueCategory;
import com.example.triage.model.TriageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class AIClassificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AIClassificationService.class);
    private static final int MAX_BODY_LENGTH = 4000;
    
    private final ChatModel chatModel;
    private final TriageConfiguration config;
    private final ObjectMapper objectMapper;
    private KnowledgeBaseService knowledgeBaseService;
    private com.example.triage.client.GitHubClient gitHubClient;
    
    public AIClassificationService(ChatModel chatModel, TriageConfiguration config) {
        this.chatModel = chatModel;
        this.config = config;
        this.objectMapper = new ObjectMapper();
    }
    
    // Setter injection to avoid circular dependency
    public void setKnowledgeBaseService(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }
    
    public void setGitHubClient(com.example.triage.client.GitHubClient gitHubClient) {
        this.gitHubClient = gitHubClient;
    }
    
    @Retryable(
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public TriageResult classifyIssue(GitHubIssue issue) {
        return classifyIssue(issue, null);
    }
    
    @Retryable(
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public TriageResult classifyIssue(GitHubIssue issue, String modelOverride) {
        // Note: Model override is logged but Spring AI uses configured model
        // To support runtime model switching, would need dynamic ChatModel creation
        String modelToUse = modelOverride != null ? modelOverride : config.getAi().getModel();
        logger.info("Classifying issue #{}: {} (requested model: {}, using: {})", 
            issue.getNumber(), issue.getTitle(), modelToUse, config.getAi().getModel());
        
        TriageResult result = new TriageResult();
        result.setIssueNumber(issue.getNumber());
        result.setIssueTitle(issue.getTitle());
        result.setIssueUrl(issue.getUrl());
        
        try {
            // Get classification
            String classificationPrompt = buildClassificationPrompt(issue);
            String classificationResponse = callLLM(classificationPrompt);
            parseClassificationResponse(classificationResponse, result);
            
            // Get response suggestion
            String responseSuggestionPrompt = buildResponseSuggestionPrompt(issue, result.getCategory());
            String responseSuggestion = callLLM(responseSuggestionPrompt);
            result.setResponseSuggestion(responseSuggestion.trim());
            
            // Generate suggested labels
            result.setSuggestedLabels(generateSuggestedLabels(result.getCategory(), issue));
            
            // Assess reproducibility for bugs and usability issues
            if (result.getCategory() == IssueCategory.BUG || 
                result.getCategory() == IssueCategory.USABILITY) {
                assessReproducibility(issue, result);
            }
            
            // Check for duplicates using knowledge base
            checkForDuplicates(issue, result);
            
            logger.info("Issue #{} classified as {} with confidence {}", 
                issue.getNumber(), result.getCategory(), result.getConfidence());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error classifying issue #{}: {}", issue.getNumber(), e.getMessage(), e);
            throw new RuntimeException("Failed to classify issue", e);
        }
    }
    
    private String callLLM(String prompt) {
        Prompt chatPrompt = new Prompt(prompt);
        return chatModel.call(chatPrompt).getResult().getOutput().getContent();
    }
    
    private String buildClassificationPrompt(GitHubIssue issue) {
        String body = truncateBody(issue.getBody());
        
        // Fetch all comments for this issue
        String allComments = "";
        if (gitHubClient != null) {
            String comments = gitHubClient.fetchOrgMemberComments(issue.getNumber(), 20);
            if (comments != null && !comments.isEmpty()) {
                allComments = "\n\nISSUE COMMENTS:\n" + comments + 
                             "\n\nConsider these comments when classifying the issue.\n";
            }
        }
        
        // Add knowledge base context if available
        String kbContext = "";
        if (knowledgeBaseService != null && knowledgeBaseService.hasKnowledgeBase()) {
            kbContext = "\n\nKNOWLEDGE BASE CONTEXT:\n" + 
                       knowledgeBaseService.getKnowledgeBaseContext() + 
                       "\n\nUse this historical context to inform your classification.\n";
        }
        
        return String.format("""
            Analyze the following GitHub issue and classify it into exactly one category:
            - Bug: Technical problems, errors, or unexpected behavior
            - Feature Request: New functionality or enhancement requests
            - Usability: User experience issues or interface improvements
            - Question: Requests for information or clarification
            %s%s
            
            Issue Title: %s
            Issue Body: %s
            Issue Author: %s
            
            Respond in JSON format:
            {
              "category": "<category>",
              "confidence": <0-100>,
              "reasoning": "<explanation>"
            }
            """, allComments, kbContext, issue.getTitle(), body, issue.getAuthor());
    }
    
    private String buildResponseSuggestionPrompt(GitHubIssue issue, IssueCategory category) {
        String body = truncateBody(issue.getBody());
        String guidelines = getGuidelinesForCategory(category);
        
        return String.format("""
            Generate a professional response for this GitHub issue classified as %s.
            
            Issue Title: %s
            Issue Body: %s
            
            Guidelines:
            %s
            
            Generate a response suggestion (2-4 sentences):
            """, category.getDisplayName(), issue.getTitle(), body, guidelines);
    }
    
    private String getGuidelinesForCategory(IssueCategory category) {
        return switch (category) {
            case BUG -> "- Ask for reproduction steps, environment details, error logs";
            case FEATURE_REQUEST -> "- Acknowledge the request, ask about use cases";
            case USABILITY -> "- Show empathy, discuss potential improvements";
            case QUESTION -> "- Provide direct answers or point to documentation";
        };
    }
    
    private java.util.List<String> generateSuggestedLabels(IssueCategory category, GitHubIssue issue) {
        java.util.List<String> labels = new java.util.ArrayList<>();
        
        // Add category-based label
        switch (category) {
            case BUG -> labels.add("bug");
            case FEATURE_REQUEST -> labels.add("enhancement");
            case USABILITY -> labels.add("usability");
            case QUESTION -> labels.add("question");
        }
        
        // Add priority label based on keywords
        String titleLower = issue.getTitle().toLowerCase();
        String bodyLower = issue.getBody() != null ? issue.getBody().toLowerCase() : "";
        
        if (titleLower.contains("crash") || titleLower.contains("critical") || 
            bodyLower.contains("crash") || bodyLower.contains("critical")) {
            labels.add("priority: high");
        }
        
        // Add platform labels
        if (titleLower.contains("windows") || bodyLower.contains("windows") || bodyLower.contains("win32")) {
            labels.add("platform: windows");
        }
        if (titleLower.contains("mac") || titleLower.contains("macos") || bodyLower.contains("darwin")) {
            labels.add("platform: mac");
        }
        if (titleLower.contains("linux") || bodyLower.contains("linux")) {
            labels.add("platform: linux");
        }
        
        // Add needs-info if confidence is low
        if (category == IssueCategory.BUG) {
            labels.add("needs-reproduction");
        }
        
        return labels;
    }
    
    private void assessReproducibility(GitHubIssue issue, TriageResult result) {
        try {
            String body = truncateBody(issue.getBody());
            
            String prompt = String.format("""
                Assess how easily this issue can be reproduced based on the information provided.
                
                Issue Title: %s
                Issue Body: %s
                
                Consider:
                - Are reproduction steps provided?
                - Is the environment/setup clearly described?
                - Are error messages or screenshots included?
                - Is the issue intermittent or consistent?
                
                Respond in JSON format:
                {
                  "reproducibility": "<Easy|Moderate|Difficult|Unknown>",
                  "notes": "<brief explanation>"
                }
                """, issue.getTitle(), body);
            
            String response = callLLM(prompt);
            parseReproducibilityResponse(response, result);
            
        } catch (Exception e) {
            logger.warn("Failed to assess reproducibility for issue #{}: {}", 
                issue.getNumber(), e.getMessage());
            result.setReproducibility("Unknown");
            result.setReproducibilityNotes("Unable to assess reproducibility");
        }
    }
    
    private void checkForDuplicates(GitHubIssue issue, TriageResult result) {
        try {
            if (knowledgeBaseService == null || !knowledgeBaseService.hasKnowledgeBase()) {
                logger.debug("No knowledge base available for duplicate detection");
                return;
            }
            
            String kbContext = knowledgeBaseService.getKnowledgeBaseContext();
            String body = truncateBody(issue.getBody());
            
            String prompt = String.format("""
                Check if this issue is a duplicate of any existing issues in the knowledge base.
                
                Current Issue:
                Title: %s
                Body: %s
                
                Knowledge Base Context:
                %s
                
                Analyze if this issue describes the same problem or request as any existing issues.
                Consider:
                - Similar symptoms or error messages
                - Same feature requests
                - Identical use cases
                
                Respond in JSON format:
                {
                  "isDuplicate": <true|false>,
                  "duplicateOf": [<issue_numbers>],
                  "reasoning": "<explanation>"
                }
                """, issue.getTitle(), body, kbContext.substring(0, Math.min(kbContext.length(), 2000)));
            
            String response = callLLM(prompt);
            parseDuplicateResponse(response, result);
            
        } catch (Exception e) {
            logger.warn("Failed to check for duplicates for issue #{}: {}", 
                issue.getNumber(), e.getMessage());
        }
    }
    
    private void parseDuplicateResponse(String response, TriageResult result) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            
            boolean isDuplicate = jsonNode.get("isDuplicate").asBoolean();
            result.setDuplicate(isDuplicate);
            
            if (isDuplicate && jsonNode.has("duplicateOf")) {
                java.util.List<Integer> duplicateOf = new java.util.ArrayList<>();
                jsonNode.get("duplicateOf").forEach(node -> duplicateOf.add(node.asInt()));
                result.setDuplicateOf(duplicateOf);
            }
            
            if (jsonNode.has("reasoning")) {
                String reasoning = jsonNode.get("reasoning").asText();
                result.setDuplicateReasoning(reasoning);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to parse duplicate detection response: {}", e.getMessage());
            result.setDuplicate(false);
        }
    }
    
    private void parseReproducibilityResponse(String response, TriageResult result) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            
            String reproducibility = jsonNode.get("reproducibility").asText();
            result.setReproducibility(reproducibility);
            
            if (jsonNode.has("notes")) {
                String notes = jsonNode.get("notes").asText();
                result.setReproducibilityNotes(notes);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to parse reproducibility response: {}", e.getMessage());
            result.setReproducibility("Unknown");
            result.setReproducibilityNotes("Unable to parse assessment");
        }
    }
    
    private void parseClassificationResponse(String response, TriageResult result) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            
            String categoryStr = jsonNode.get("category").asText();
            IssueCategory category = IssueCategory.fromString(categoryStr);
            result.setCategory(category);
            
            int confidence = jsonNode.get("confidence").asInt();
            result.setConfidence(confidence);
            
            String reasoning = jsonNode.get("reasoning").asText();
            result.setReasoning(reasoning);
            
        } catch (Exception e) {
            logger.warn("Failed to parse LLM response, flagging for manual review: {}", e.getMessage());
            result.setCategory(IssueCategory.QUESTION);
            result.setConfidence(0);
            result.setReasoning("Failed to parse AI response");
            result.setFlaggedForManualReview(true);
        }
    }
    
    private String truncateBody(String body) {
        if (body == null) {
            return "";
        }
        if (body.length() > MAX_BODY_LENGTH) {
            return body.substring(0, MAX_BODY_LENGTH) + "... [truncated]";
        }
        return body;
    }
}
