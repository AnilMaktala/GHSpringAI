# Updated Bulk Categorization Feature

## Changes Made

### 1. Limited to First 10 Issues
- Changed bulk categorization to process only the **first 10 pending-triage issues**
- Simplified API to remove pagination parameters
- Updated UI to reflect the 10-issue limit

### 2. GitHub Label Update Functionality

#### Backend Changes

**GitHubClient.java** - Added new method:
- `updateIssueLabels(int issueNumber, List<String> labelsToAdd, List<String> labelsToRemove)`
  - Fetches current issue labels
  - Removes specified labels (e.g., "pending-triage")
  - Adds new labels (e.g., "type: bug", "type: feature")
  - Updates the issue on GitHub via API

**TriageController.java** - Added new endpoint:
- `POST /api/triage/update-labels/{issueNumber}?category={category}`
  - Takes issue number and category as parameters
  - Maps category to appropriate label:
    - BUG → "type: bug"
    - FEATURE_REQUEST → "type: feature"
    - QUESTION → "type: question"
    - USABILITY → "type: usability"
  - Removes "pending-triage" label
  - Adds the category-specific type label

#### Frontend Changes

**categorize.html**:
- Updated header to reflect "first 10 issues" and label update capability
- Page size changed to 10 for display

**categorize.js**:
- Simplified `categorizeAllPages()` to fetch all 10 issues in one call
- Added `updateLabels(issueNumber, category)` function
  - Calls the backend API to update labels
  - Shows confirmation dialog before updating
  - Updates UI to show "✓ Labels Updated" after success
- Added "Update Labels" button to each issue card
- Button is replaced with "✓ Labels Updated" status after successful update
- Added `getCategoryLabel(category)` helper function

## How to Use

### 1. Access the Categorization Page
- Navigate to http://localhost:8080/categorize.html
- Or click "🔍 Bulk Categorization" from the main dashboard

### 2. Categorize Issues
- Click "Start Categorization" button
- Wait for AI to categorize the first 10 pending-triage issues
- View results with category badges, confidence scores, and reasoning

### 3. Update GitHub Labels
- For each categorized issue, click the "Update Labels" button
- Confirm the action in the dialog
- The system will:
  - Remove the "pending-triage" label
  - Add the appropriate "type: X" label (bug, feature, question, or usability)
- Button changes to "✓ Labels Updated" after success

## Label Mapping

| Category | GitHub Label |
|----------|--------------|
| BUG | type: bug |
| FEATURE_REQUEST | type: feature |
| QUESTION | type: question |
| USABILITY | type: usability |

## API Endpoints

### POST /api/triage/categorize-all
- Categorizes the first 10 pending-triage issues
- No parameters required
- Returns: Array of categorization results

### POST /api/triage/update-labels/{issueNumber}
- Updates labels for a specific issue
- Parameters:
  - `issueNumber` (path) - The GitHub issue number
  - `category` (query) - The category (BUG, FEATURE_REQUEST, QUESTION, USABILITY)
- Returns: Success status and updated label information

## Features

✅ Processes first 10 pending-triage issues only
✅ AI-powered categorization with confidence scores
✅ One-click label updates directly to GitHub
✅ Removes "pending-triage" label automatically
✅ Adds appropriate "type: X" label based on category
✅ Visual feedback showing which issues have been labeled
✅ Confirmation dialog before updating labels
✅ Error handling with user-friendly messages

## Technical Details

- Uses GitHub REST API v3 for label updates
- Requires valid GitHub token with repo permissions
- Implements retry logic for API calls
- Handles rate limiting gracefully
- Updates are atomic (all-or-nothing per issue)

## Example Workflow

1. User clicks "Start Categorization"
2. System fetches first 10 pending-triage issues
3. AI categorizes each issue (Bug, Feature, Question, or Usability)
4. Results displayed with confidence scores and reasoning
5. User reviews each categorization
6. User clicks "Update Labels" for issues they agree with
7. System removes "pending-triage" and adds "type: X" label
8. Issue card updates to show "✓ Labels Updated"
9. Changes are immediately visible on GitHub
