# Implementation Plan

- [x] 1. Add GitHub API method to fetch single issue by ID
  - Create `fetchIssueById(int issueNumber)` method in GitHubClient class
  - Implement GET request to `/repos/{owner}/{repo}/issues/{issueNumber}` endpoint
  - Add error handling for 404 (issue not found), 401/403 (auth errors), and 429 (rate limits)
  - Apply @Retryable annotation for transient failures
  - _Requirements: 1.2, 1.3, 3.1_

- [x] 2. Add REST endpoint for issue search and triage
  - Create `searchAndTriageIssue(@PathVariable int issueId)` endpoint in TriageController
  - Implement input validation for positive integer issue IDs
  - Call GitHubClient.fetchIssueById() to retrieve the issue
  - Call AIClassificationService.classifyIssue() to categorize the issue
  - Format response with issue details and triage result
  - Implement comprehensive error handling with specific error codes
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 3.3, 3.4, 3.5_

- [x] 3. Add search UI components to categorize.html
  - Create search section HTML with input field and buttons above the filter bar
  - Add result card container for displaying search results
  - Style search components to match existing UI design
  - Add loading state overlay for search operations
  - Add error message display area
  - _Requirements: 1.1, 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 4. Implement search functionality in categorize.js
  - Create `searchIssue()` function to handle search button click
  - Implement input validation for positive integers
  - Make POST request to `/api/triage/search-issue/{issueId}` endpoint
  - Show loading indicator during API call
  - Create `displaySearchResult(data)` function to render triage results
  - Create `displaySearchError(error)` function to show user-friendly error messages
  - Create `clearSearch()` function to reset search state
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 2.4, 2.5_

- [ ]* 5. Add unit tests for backend components
  - Write unit tests for GitHubClient.fetchIssueById() covering success and error cases
  - Write unit tests for TriageController.searchAndTriageIssue() endpoint
  - Test input validation, error handling, and response formatting
  - _Requirements: 3.1, 3.3_
