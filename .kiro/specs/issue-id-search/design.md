# Design Document

## Overview

This feature adds a search-by-issue-ID capability to the GitHub Issue Triage page, allowing users to quickly triage individual issues by entering their issue number. The implementation will integrate seamlessly with the existing triage infrastructure, reusing the GitHubClient, AIClassificationService, and UI components.

## Architecture

### High-Level Flow

1. User enters an issue ID in a search input field on the triage page
2. Frontend validates the input and sends a request to a new backend endpoint
3. Backend fetches the specific issue from GitHub API using GitHubClient
4. Backend performs AI classification using AIClassificationService
5. Backend returns the triage result to the frontend
6. Frontend displays the result in a dedicated result card

### Component Interaction

```
[UI Search Input] 
    ↓ (POST /api/triage/search-issue/{issueId})
[TriageController - New Endpoint]
    ↓ (fetch issue)
[GitHubClient.fetchIssueById()]
    ↓ (classify)
[AIClassificationService.classifyIssue()]
    ↓ (return result)
[UI Result Display]
```

## Components and Interfaces

### Backend Components

#### 1. GitHubClient - New Method

Add a new method to fetch a single issue by ID:

```java
public GitHubIssue fetchIssueById(int issueNumber)
```

**Responsibilities:**
- Make GET request to `/repos/{owner}/{repo}/issues/{issueNumber}`
- Handle authentication with GitHub token
- Parse response into GitHubIssue object
- Handle errors (404 for not found, 401/403 for auth issues, 429 for rate limits)
- Apply retry logic for transient failures

**Error Handling:**
- Throw GitHubApiException with appropriate error codes
- Return null or throw specific exception for 404 (issue not found)

#### 2. TriageController - New Endpoint

Add a new REST endpoint:

```java
@PostMapping("/search-issue/{issueId}")
public ResponseEntity<Map<String, Object>> searchAndTriageIssue(@PathVariable int issueId)
```

**Request:**
- Path parameter: `issueId` (integer)

**Response:**
```json
{
  "success": true,
  "issue": {
    "number": 123,
    "title": "Issue title",
    "url": "https://github.com/...",
    "author": "username",
    "createdAt": "2025-11-05T..."
  },
  "triageResult": {
    "category": "BUG",
    "confidence": 85,
    "reasoning": "...",
    "suggestedLabels": ["bug", "needs-reproduction"],
    "responseSuggestion": "...",
    "reproducibility": "Easy",
    "isDuplicate": false
  }
}
```

**Error Response:**
```json
{
  "success": false,
  "error": "Issue #123 not found",
  "errorCode": "ISSUE_NOT_FOUND"
}
```

**Error Codes:**
- `ISSUE_NOT_FOUND`: Issue doesn't exist in repository
- `INVALID_ISSUE_ID`: Issue ID is not a valid positive integer
- `GITHUB_API_ERROR`: GitHub API returned an error
- `CLASSIFICATION_ERROR`: AI classification failed
- `RATE_LIMIT_EXCEEDED`: GitHub API rate limit hit

**Responsibilities:**
- Validate issue ID (must be positive integer)
- Call GitHubClient to fetch issue
- Call AIClassificationService to classify issue
- Format response with both issue details and triage result
- Handle and format errors appropriately

### Frontend Components

#### 1. UI Elements (categorize.html)

Add a search section above the existing filter bar:

```html
<div class="search-section">
  <div class="search-container">
    <input type="number" 
           id="issueIdInput" 
           placeholder="Enter issue number (e.g., 123)" 
           min="1">
    <button class="btn btn-primary" onclick="searchIssue()">
      🔍 Search & Triage
    </button>
    <button class="btn btn-secondary" onclick="clearSearch()">
      Clear
    </button>
  </div>
</div>

<div id="searchResult" class="search-result" style="display: none;">
  <!-- Result card will be dynamically populated -->
</div>
```

**Styling:**
- Search container with flexbox layout
- Input field with number validation
- Buttons styled consistently with existing UI
- Result card with similar styling to issue cards
- Loading state indicator
- Error message display area

#### 2. JavaScript Functions (categorize.js)

**searchIssue():**
- Validate input (must be positive integer)
- Show loading indicator
- Make POST request to `/api/triage/search-issue/{issueId}`
- Handle response and display result
- Handle errors and show user-friendly messages

**clearSearch():**
- Clear input field
- Hide result card
- Reset any error messages

**displaySearchResult(data):**
- Create and populate result card HTML
- Show category badge, confidence bar
- Display reasoning, suggested labels
- Show reproducibility assessment (if applicable)
- Show duplicate detection results (if applicable)
- Provide link to GitHub issue
- Add "Update Labels" button

**displaySearchError(error):**
- Show error message in user-friendly format
- Provide actionable guidance (e.g., "Check issue number")

## Data Models

### GitHubIssue (Existing)

No changes needed - already contains all required fields.

### TriageResult (Existing)

No changes needed - already contains all required fields including:
- category
- confidence
- reasoning
- suggestedLabels
- responseSuggestion
- reproducibility
- isDuplicate
- duplicateOf

## Error Handling

### Backend Error Handling

1. **Invalid Issue ID:**
   - Validate that issue ID is a positive integer
   - Return 400 Bad Request with error message

2. **Issue Not Found:**
   - Catch 404 from GitHub API
   - Return 404 with user-friendly message

3. **GitHub API Errors:**
   - Handle authentication failures (401/403)
   - Handle rate limiting (429) with retry-after information
   - Handle network timeouts with retry logic
   - Return 502 Bad Gateway for GitHub API failures

4. **Classification Errors:**
   - Catch exceptions from AIClassificationService
   - Return 500 Internal Server Error with generic message
   - Log detailed error for debugging

### Frontend Error Handling

1. **Input Validation:**
   - Check for empty input
   - Check for non-numeric input
   - Check for negative numbers
   - Show inline validation messages

2. **Network Errors:**
   - Handle timeout errors
   - Handle connection failures
   - Show "Please try again" message

3. **API Errors:**
   - Parse error response from backend
   - Display appropriate message based on error code
   - Provide actionable guidance

## Testing Strategy

### Unit Tests

1. **GitHubClient.fetchIssueById():**
   - Test successful issue fetch
   - Test 404 handling
   - Test authentication errors
   - Test rate limiting
   - Test network failures with retry

2. **TriageController.searchAndTriageIssue():**
   - Test successful search and triage
   - Test invalid issue ID validation
   - Test issue not found handling
   - Test classification error handling
   - Test response format

3. **Frontend Functions:**
   - Test input validation
   - Test API call with valid input
   - Test error handling
   - Test result display
   - Test clear functionality

### Integration Tests

1. **End-to-End Flow:**
   - Search for existing issue
   - Verify classification result
   - Verify UI updates correctly

2. **Error Scenarios:**
   - Search for non-existent issue
   - Search with invalid input
   - Handle API failures gracefully

### Manual Testing

1. **UI/UX Testing:**
   - Test responsive design
   - Test loading states
   - Test error message display
   - Test result card layout
   - Test interaction with existing features

2. **Edge Cases:**
   - Very large issue numbers
   - Issues with minimal content
   - Issues with very long content
   - Issues with special characters

## Security Considerations

1. **Input Validation:**
   - Sanitize issue ID input to prevent injection attacks
   - Validate on both frontend and backend

2. **Authentication:**
   - Reuse existing GitHub token authentication
   - Ensure token is not exposed in responses

3. **Rate Limiting:**
   - Respect GitHub API rate limits
   - Implement client-side throttling if needed

4. **Error Messages:**
   - Don't expose sensitive information in error messages
   - Log detailed errors server-side only

## Performance Considerations

1. **Caching:**
   - Consider caching recently searched issues (optional enhancement)
   - Cache duration: 5 minutes

2. **Response Time:**
   - Target: < 3 seconds for search and triage
   - GitHub API call: ~500ms
   - AI classification: ~1-2 seconds

3. **Concurrent Requests:**
   - Support multiple users searching simultaneously
   - No shared state between requests

## UI/UX Design

### Search Section Layout

```
┌─────────────────────────────────────────────────────┐
│  Search by Issue ID                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐ │
│  │ Issue #: 123 │  │ 🔍 Search    │  │  Clear   │ │
│  └──────────────┘  └──────────────┘  └──────────┘ │
└─────────────────────────────────────────────────────┘
```

### Result Card Layout

```
┌─────────────────────────────────────────────────────┐
│  #123: Issue Title                                  │
│  [BUG] Confidence: 85%  ████████░░                  │
│                                                     │
│  Reasoning: This appears to be a bug because...     │
│                                                     │
│  Suggested Labels: bug, needs-reproduction          │
│                                                     │
│  Response Suggestion: Thank you for reporting...    │
│                                                     │
│  Reproducibility: Easy                              │
│                                                     │
│  [View on GitHub]  [Update Labels]                  │
└─────────────────────────────────────────────────────┘
```

### Loading State

- Show spinner overlay during search
- Display "Searching for issue #123..."
- Disable search button during operation

### Error State

- Show error icon with message
- Use red color scheme for errors
- Provide clear next steps

## Integration Points

1. **Existing GitHubClient:**
   - Add new method without breaking existing functionality
   - Reuse authentication and error handling patterns

2. **Existing AIClassificationService:**
   - Use existing classifyIssue() method
   - No changes needed

3. **Existing UI:**
   - Add search section above filter bar
   - Maintain consistent styling
   - Don't interfere with bulk categorization features

4. **Existing TriageController:**
   - Add new endpoint alongside existing endpoints
   - Follow existing patterns for response formatting

## Future Enhancements

1. **Search History:**
   - Store recently searched issues in browser localStorage
   - Show quick access to recent searches

2. **Batch Search:**
   - Allow searching multiple issues at once
   - Display results in a list

3. **Auto-complete:**
   - Suggest issue numbers as user types
   - Show issue titles in dropdown

4. **Deep Linking:**
   - Support URL parameter for issue ID
   - Allow direct links to search results
