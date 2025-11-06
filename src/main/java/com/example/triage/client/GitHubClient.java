package com.example.triage.client;

import com.example.triage.config.TriageConfiguration;
import com.example.triage.model.GitHubIssue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class GitHubClient {
    
    private static final Logger logger = LoggerFactory.getLogger(GitHubClient.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";
    
    private final TriageConfiguration config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public GitHubClient(TriageConfiguration config) {
        this.config = config;
        // Use HttpClient5 to support PATCH method
        this.restTemplate = new RestTemplate();
        try {
            org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient = 
                org.apache.hc.client5.http.impl.classic.HttpClients.createDefault();
            org.springframework.http.client.HttpComponentsClientHttpRequestFactory requestFactory = 
                new org.springframework.http.client.HttpComponentsClientHttpRequestFactory(httpClient);
            this.restTemplate.setRequestFactory(requestFactory);
            logger.info("Configured RestTemplate with HttpClient5 for PATCH support");
        } catch (Exception e) {
            logger.warn("Could not configure HttpClient5, PATCH requests may not work: {}", e.getMessage());
        }
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Retryable(
        retryFor = {ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public GitHubIssue fetchIssueById(int issueNumber) {
        String owner = config.getGithub().getOwner();
        String repo = config.getGithub().getRepo();
        
        logger.info("Fetching issue #{} from {}/{}", issueNumber, owner, repo);
        
        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            String url = String.format("%s/repos/%s/%s/issues/%d",
                    GITHUB_API_BASE, owner, repo, issueNumber);
            
            ResponseEntity<GitHubIssue> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    GitHubIssue.class
            );
            
            logger.info("Successfully fetched issue #{}", issueNumber);
            return response.getBody();
            
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                logger.warn("Issue #{} not found in {}/{}", issueNumber, owner, repo);
                throw new GitHubApiException("Issue #" + issueNumber + " not found", e, 4);
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || 
                       e.getStatusCode() == HttpStatus.FORBIDDEN) {
                logger.error("GitHub authentication failed: {}. Please check your GITHUB_TOKEN.", 
                    e.getMessage());
                throw new GitHubApiException("Authentication failed with GitHub API", e, 3);
            } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                String resetTime = e.getResponseHeaders() != null ? 
                    e.getResponseHeaders().getFirst("X-RateLimit-Reset") : "unknown";
                logger.error("GitHub API rate limit exceeded. Reset time: {}", resetTime);
                throw new GitHubApiException(
                    "GitHub API rate limit exceeded. Reset time: " + resetTime, e, 2);
            }
            logger.error("GitHub API error: {}", e.getMessage(), e);
            throw new GitHubApiException("GitHub API request failed", e, 1);
        } catch (ResourceAccessException e) {
            logger.warn("Network timeout, will retry: {}", e.getMessage());
            throw e; // Let @Retryable handle this
        } catch (Exception e) {
            logger.error("Unexpected error fetching issue #{}: {}", issueNumber, e.getMessage(), e);
            throw new GitHubApiException("Failed to fetch issue from GitHub", e, 1);
        }
    }
    
    @Retryable(
        retryFor = {ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<GitHubIssue> fetchPendingTriageIssues() {
        return fetchPendingTriageIssues(100); // Default to 100
    }
    
    @Retryable(
        retryFor = {ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<GitHubIssue> fetchPendingTriageIssues(int limit) {
        String owner = config.getGithub().getOwner();
        String repo = config.getGithub().getRepo();
        
        logger.info("Fetching up to {} pending-triage issues from {}/{}", limit, owner, repo);
        
        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            List<GitHubIssue> allIssues = new java.util.ArrayList<>();
            int page = 1;
            int perPage = 100; // GitHub API max per page
            
            while (allIssues.size() < limit) {
                String url = String.format("%s/repos/%s/%s/issues?labels=pending-triage&state=open&per_page=%d&page=%d&sort=created&direction=desc",
                        GITHUB_API_BASE, owner, repo, perPage, page);
                
                ResponseEntity<GitHubIssue[]> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        entity,
                        GitHubIssue[].class
                );
                
                if (response.getBody() == null || response.getBody().length == 0) {
                    break; // No more issues
                }
                
                allIssues.addAll(Arrays.asList(response.getBody()));
                
                if (response.getBody().length < perPage) {
                    break; // Last page
                }
                
                page++;
                
                // Rate limiting - pause between pages
                if (page % 3 == 0) {
                    Thread.sleep(1000);
                }
            }
            
            logger.info("Fetched {} issues with pending-triage label", allIssues.size());
            
            return allIssues.subList(0, Math.min(allIssues.size(), limit));
            
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || 
                e.getStatusCode() == HttpStatus.FORBIDDEN) {
                logger.error("GitHub authentication failed: {}. Please check your GITHUB_TOKEN.", 
                    e.getMessage());
                throw new GitHubApiException("Authentication failed with GitHub API", e, 3);
            } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                String resetTime = e.getResponseHeaders() != null ? 
                    e.getResponseHeaders().getFirst("X-RateLimit-Reset") : "unknown";
                logger.error("GitHub API rate limit exceeded. Reset time: {}", resetTime);
                throw new GitHubApiException(
                    "GitHub API rate limit exceeded. Reset time: " + resetTime, e, 2);
            }
            logger.error("GitHub API error: {}", e.getMessage(), e);
            throw new GitHubApiException("GitHub API request failed", e, 1);
        } catch (ResourceAccessException e) {
            logger.warn("Network timeout, will retry: {}", e.getMessage());
            throw e; // Let @Retryable handle this
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching issues: {}", e.getMessage());
            throw new GitHubApiException("Interrupted while fetching issues", e, 1);
        } catch (Exception e) {
            logger.error("Unexpected error fetching issues from GitHub: {}", e.getMessage(), e);
            throw new GitHubApiException("Failed to fetch issues from GitHub", e, 1);
        }
    }
    
    @Retryable(
        retryFor = {ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<GitHubIssue> fetchClosedIssues(int limit) {
        String owner = config.getGithub().getOwner();
        String repo = config.getGithub().getRepo();
        
        String url = String.format("%s/repos/%s/%s/issues?state=closed&per_page=%d&sort=updated&direction=desc",
                GITHUB_API_BASE, owner, repo, Math.min(limit, 100));
        
        logger.info("Fetching up to {} closed issues from {}/{}", limit, owner, repo);
        
        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            List<GitHubIssue> allIssues = new java.util.ArrayList<>();
            int page = 1;
            int perPage = 100;
            
            while (allIssues.size() < limit) {
                String pagedUrl = url + "&page=" + page;
                
                ResponseEntity<GitHubIssue[]> response = restTemplate.exchange(
                        pagedUrl,
                        HttpMethod.GET,
                        entity,
                        GitHubIssue[].class
                );
                
                if (response.getBody() == null || response.getBody().length == 0) {
                    break;
                }
                
                allIssues.addAll(Arrays.asList(response.getBody()));
                
                if (response.getBody().length < perPage) {
                    break; // Last page
                }
                
                page++;
                
                // Rate limiting
                if (page % 3 == 0) {
                    Thread.sleep(1000);
                }
            }
            
            logger.info("Fetched {} closed issues", allIssues.size());
            return allIssues.subList(0, Math.min(allIssues.size(), limit));
            
        } catch (HttpClientErrorException e) {
            logger.error("GitHub API error: {}", e.getMessage(), e);
            throw new GitHubApiException("GitHub API request failed", e, 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching issues", e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching closed issues: {}", e.getMessage(), e);
            throw new GitHubApiException("Failed to fetch closed issues", e, 1);
        }
    }
    
    @Retryable(
        retryFor = {ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<GitHubIssue> fetchTriagedIssues(int limit) {
        String owner = config.getGithub().getOwner();
        String repo = config.getGithub().getRepo();
        
        logger.info("Fetching up to {} triaged issues (without pending-triage label) from {}/{}", limit, owner, repo);
        
        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            List<GitHubIssue> allIssues = new java.util.ArrayList<>();
            int page = 1;
            int perPage = 100;
            
            // Fetch both open and closed issues, then filter out pending-triage
            for (String state : Arrays.asList("open", "closed")) {
                page = 1;
                while (allIssues.size() < limit) {
                    String url = String.format("%s/repos/%s/%s/issues?state=%s&per_page=%d&page=%d&sort=updated&direction=desc",
                            GITHUB_API_BASE, owner, repo, state, perPage, page);
                    
                    ResponseEntity<GitHubIssue[]> response = restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            entity,
                            GitHubIssue[].class
                    );
                    
                    if (response.getBody() == null || response.getBody().length == 0) {
                        break;
                    }
                    
                    // Filter out issues with pending-triage label
                    for (GitHubIssue issue : response.getBody()) {
                        boolean hasPendingTriage = issue.getLabels().stream()
                            .anyMatch(label -> "pending-triage".equals(label.getName()));
                        
                        if (!hasPendingTriage) {
                            allIssues.add(issue);
                            if (allIssues.size() >= limit) {
                                break;
                            }
                        }
                    }
                    
                    if (response.getBody().length < perPage) {
                        break; // Last page
                    }
                    
                    page++;
                    
                    // Rate limiting
                    if (page % 3 == 0) {
                        Thread.sleep(1000);
                    }
                }
                
                if (allIssues.size() >= limit) {
                    break;
                }
            }
            
            logger.info("Fetched {} triaged issues", allIssues.size());
            return allIssues.subList(0, Math.min(allIssues.size(), limit));
            
        } catch (HttpClientErrorException e) {
            logger.error("GitHub API error: {}", e.getMessage(), e);
            throw new GitHubApiException("GitHub API request failed", e, 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching issues", e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching triaged issues: {}", e.getMessage(), e);
            throw new GitHubApiException("Failed to fetch triaged issues", e, 1);
        }
    }
    
    @Retryable(
        retryFor = {ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String fetchOrgMemberComments(Long issueNumber, int maxComments) {
        String owner = config.getGithub().getOwner();
        String repo = config.getGithub().getRepo();
        
        String url = String.format("%s/repos/%s/%s/issues/%d/comments?per_page=%d",
                GITHUB_API_BASE, owner, repo, issueNumber, Math.min(maxComments, 100));
        
        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );
            
            if (response.getBody() == null) {
                return "";
            }
            
            // Parse comments and filter for org members
            JsonNode comments = objectMapper.readTree(response.getBody());
            StringBuilder orgComments = new StringBuilder();
            
            for (JsonNode comment : comments) {
                JsonNode user = comment.get("user");
                String authorAssociation = comment.has("author_association") ? 
                    comment.get("author_association").asText() : "";
                
                // Include comments from OWNER, MEMBER, COLLABORATOR
                if (authorAssociation.equals("OWNER") || 
                    authorAssociation.equals("MEMBER") || 
                    authorAssociation.equals("COLLABORATOR")) {
                    
                    String username = user.get("login").asText();
                    String body = comment.get("body").asText();
                    orgComments.append(String.format("[%s]: %s\n", username, body));
                }
            }
            
            return orgComments.toString();
            
        } catch (Exception e) {
            logger.warn("Failed to fetch comments for issue #{}: {}", issueNumber, e.getMessage());
            return "";
        }
    }
    
    @Retryable(
        retryFor = {ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void updateIssueLabels(int issueNumber, List<String> labelsToAdd, List<String> labelsToRemove) {
        String owner = config.getGithub().getOwner();
        String repo = config.getGithub().getRepo();
        
        logger.info("Updating labels for issue #{}: adding {}, removing {}", 
            issueNumber, labelsToAdd, labelsToRemove);
        
        try {
            HttpHeaders headers = createAuthHeaders();
            headers.set("Content-Type", "application/json");
            
            // First, get current labels
            String getUrl = String.format("%s/repos/%s/%s/issues/%d",
                    GITHUB_API_BASE, owner, repo, issueNumber);
            
            HttpEntity<String> getEntity = new HttpEntity<>(headers);
            ResponseEntity<String> getResponse = restTemplate.exchange(
                    getUrl,
                    HttpMethod.GET,
                    getEntity,
                    String.class
            );
            
            JsonNode issueData = objectMapper.readTree(getResponse.getBody());
            JsonNode currentLabels = issueData.get("labels");
            
            // Build new label list
            List<String> newLabels = new java.util.ArrayList<>();
            for (JsonNode label : currentLabels) {
                String labelName = label.get("name").asText();
                if (!labelsToRemove.contains(labelName)) {
                    newLabels.add(labelName);
                }
            }
            
            // Add new labels
            for (String label : labelsToAdd) {
                if (!newLabels.contains(label)) {
                    newLabels.add(label);
                }
            }
            
            // Update labels
            String updateUrl = String.format("%s/repos/%s/%s/issues/%d",
                    GITHUB_API_BASE, owner, repo, issueNumber);
            
            String requestBody = objectMapper.writeValueAsString(
                Collections.singletonMap("labels", newLabels)
            );
            
            HttpEntity<String> updateEntity = new HttpEntity<>(requestBody, headers);
            restTemplate.exchange(
                    updateUrl,
                    HttpMethod.PATCH,
                    updateEntity,
                    String.class
            );
            
            logger.info("Successfully updated labels for issue #{}", issueNumber);
            
        } catch (HttpClientErrorException e) {
            logger.error("GitHub API error updating labels for issue #{}: {}", 
                issueNumber, e.getMessage());
            throw new GitHubApiException("Failed to update issue labels", e, 1);
        } catch (Exception e) {
            logger.error("Unexpected error updating labels for issue #{}: {}", 
                issueNumber, e.getMessage(), e);
            throw new GitHubApiException("Failed to update issue labels", e, 1);
        }
    }
    
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + config.getGithub().getToken());
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return headers;
    }
}
