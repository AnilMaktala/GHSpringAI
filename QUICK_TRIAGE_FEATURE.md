# Quick Triage Feature

## Overview
Added a high-level quick triage feature that categorizes pending-triage issues based ONLY on title and description, providing faster categorization without deep analysis. Results are stored in a markdown file and displayed in the UI.

## What Was Added

### New Service: QuickTriageService

**File:** `src/main/java/com/example/triage/service/QuickTriageService.java`

This service provides fast, lightweight categorization of issues.

#### Key Features:

1. **Quick Categorization**
   - Reviews only title and description (no comments, no deep analysis)
   - Uses AI to categorize into: BUG, FEATURE, QUESTION, or USABILITY
   - Much faster than full categorization
   - Returns brief one-sentence reasoning

2. **Markdown Storage**
   - Saves results to `quick-triage.md`
   - Session-based logging with timestamps
   - Category distribution summary
   - Preserves history (append-only)

3. **Result Loading**
   - Can load previously saved quick triage results
   - Parses markdown file to extract data
   - Displays in UI automatically on page load

### New API Endpoints

#### POST /api/triage/quick-triage
- Performs quick triage on first 10 pending-triage issues
- Parameters:
  - `limit` (optional, default: 10) - Number of issues to triage
- Returns: Array of quick triage results

#### GET /api/triage/quick-triage-results
- Loads previously saved quick triage results from markdown file
- No parameters required
- Returns: Array of quick triage results from last session

### Updated UI

**File:** `src/main/resources/static/categorize.html`

Added new button:
- **⚡ Quick Triage (Title Only)** - Fast categorization button
- **🔍 Full Categorization** - Existing detailed categorization

**File:** `src/main/resources/static/categorize.js`

New functions:
- `startQuickTriage()` - Initiates quick triage process
- `loadQuickTriageResults()` - Loads existing results on page load
- Converts quick triage results to display format
- Updates summary cards and filters

## Quick Triage vs Full Categorization

| Feature | Quick Triage | Full Categorization |
|---------|-------------|---------------------|
| **Speed** | Fast (~2-3 seconds per issue) | Slow (~10-15 seconds per issue) |
| **Analysis Depth** | Title + Description only | Full issue + comments + context |
| **Confidence** | Lower (75% default) | Higher (AI-calculated) |
| **Duplicate Detection** | No | Yes |
| **Reproducibility** | No | Yes (for bugs) |
| **Suggested Labels** | No | Yes |
| **Knowledge Base** | Not used | Used for context |
| **Best For** | Initial sorting, large batches | Final categorization, accuracy |

## Markdown File Format

### Example: quick-triage.md

```markdown
# Quick Triage Results

This file contains quick categorization of pending-triage issues based on title and description only.

---

## Quick Triage Session - 2025-11-04 19:55:30

**Total Issues:** 10

**Category Distribution:**
- 🐛 Bugs: 6
- ✨ Features: 2
- ❓ Questions: 1
- 🎨 Usability: 1

### Issues

#### 🐛 #1234: Application crashes on startup

- **Category:** BUG
- **Reason:** Application crashes when clicking the submit button
- **Author:** user123
- **URL:** [https://github.com/owner/repo/issues/1234](https://github.com/owner/repo/issues/1234)

#### ✨ #1235: Add dark mode support

- **Category:** FEATURE
- **Reason:** User is requesting a new dark mode feature
- **Author:** user456
- **URL:** [https://github.com/owner/repo/issues/1235](https://github.com/owner/repo/issues/1235)

---
```

## How to Use

### 1. Quick Triage from UI

1. Navigate to http://localhost:8080/categorize.html
2. Click **⚡ Quick Triage (Title Only)** button
3. Confirm the action
4. Wait for quick categorization to complete (~30 seconds for 10 issues)
5. View results with category badges and reasoning
6. Results are automatically saved to `quick-triage.md`

### 2. View Previous Results

- Results from the last quick triage session are automatically loaded when you open the categorize page
- No need to re-run quick triage to see previous results

### 3. Update Labels

- After quick triage, you can still use the "Update Labels" button on each issue
- This will remove "pending-triage" and add the appropriate "type: X" label

## Workflow Recommendations

### Recommended Workflow:

1. **Quick Triage First** (⚡ button)
   - Get a fast overview of all pending issues
   - Identify obvious bugs, features, questions
   - Takes ~30 seconds for 10 issues

2. **Review Results**
   - Check if categorizations make sense
   - Look for any that need deeper analysis

3. **Full Categorization for Uncertain Issues** (🔍 button)
   - Use for issues where quick triage wasn't confident
   - Use for complex issues that need deeper analysis
   - Takes ~2-3 minutes for 10 issues

4. **Update Labels**
   - Click "Update Labels" on issues you're confident about
   - Apply changes to GitHub

## Benefits

✅ **Speed**
- 5-10x faster than full categorization
- Can process large batches quickly

✅ **Good Enough for Initial Sorting**
- Accurate enough for first-pass categorization
- Helps prioritize which issues need deeper analysis

✅ **Persistent Storage**
- Results saved to markdown file
- Can review later without re-running

✅ **Automatic Loading**
- Previous results load automatically
- No need to re-categorize on page refresh

✅ **Two-Tier Approach**
- Quick triage for overview
- Full categorization for accuracy

## Technical Details

- **AI Model:** Uses same model as full categorization (Claude Haiku 3.5)
- **Prompt:** Simplified prompt focusing only on title and description
- **Storage:** Plain markdown file for easy reading and version control
- **Parsing:** Custom markdown parser to load results back into UI
- **Default Confidence:** 75% (since it's based on limited information)

## File Locations

- **Quick Triage Results:** `quick-triage.md` (project root)
- **Full Triage Results:** `triage-report.md` (project root)

## Future Enhancements

- Batch processing of more than 10 issues
- Confidence scoring based on title/description quality
- Integration with full categorization (upgrade quick triage to full)
- Export to different formats
- Comparison view (quick vs full categorization)
