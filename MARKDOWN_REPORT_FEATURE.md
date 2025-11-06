# Markdown Report Feature

## Overview
Added functionality to automatically save all triaged issues to a Markdown file (`triage-report.md`) for easy review and documentation.

## What Was Added

### New Service: TriageReportService

**File:** `src/main/java/com/example/triage/service/TriageReportService.java`

This service handles the creation and updating of the triage report in Markdown format.

#### Key Features:

1. **Automatic Report Creation**
   - Creates `triage-report.md` if it doesn't exist
   - Adds a header explaining the file's purpose

2. **Session-Based Logging**
   - Each categorization session is logged with a timestamp
   - Sessions are appended to the file (history is preserved)

3. **Comprehensive Issue Details**
   - Issue number and title
   - Category with emoji indicators (🐛 Bug, ✨ Feature, ❓ Question, 🎨 Usability)
   - Confidence score
   - Direct link to GitHub issue
   - AI reasoning
   - Duplicate detection warnings
   - Suggested labels
   - Reproducibility assessment (for bugs)

4. **Category Summary**
   - Each session includes a distribution summary
   - Shows count of issues by category

### Updated Controller

**File:** `src/main/java/com/example/triage/controller/TriageController.java`

- Injected `TriageReportService` into the controller
- Updated `categorizeAllPendingIssues()` endpoint to save results after categorization
- Success message now indicates results were saved to markdown file

## Report Format

### Example Output

```markdown
# GitHub Issue Triage Report

This file contains the history of all triaged issues.

---

## Triage Session - 2025-11-04 19:45:30

**Total Issues Triaged:** 10

**Category Distribution:**
- 🐛 Bugs: 7
- ✨ Features: 2
- ❓ Questions: 1
- 🎨 Usability: 0

### Triaged Issues

#### 🐛 Issue #1234: Application crashes on startup

- **Category:** Bug
- **Confidence:** 95%
- **URL:** [https://github.com/owner/repo/issues/1234](https://github.com/owner/repo/issues/1234)
- **Reasoning:** The issue describes a crash with stack trace, indicating a bug
- **Suggested Labels:** bug, crash, high-priority
- **Reproducibility:** Easy
  - Clear steps provided with expected vs actual behavior

#### ✨ Issue #1235: Add dark mode support

- **Category:** Feature Request
- **Confidence:** 92%
- **URL:** [https://github.com/owner/repo/issues/1235](https://github.com/owner/repo/issues/1235)
- **Reasoning:** User is requesting a new feature for dark mode
- **Suggested Labels:** enhancement, ui, feature

---

## Triage Session - 2025-11-04 20:15:45

**Total Issues Triaged:** 10

...
```

## How It Works

1. **User Triggers Categorization**
   - User clicks "Start Categorization" on the UI
   - Backend processes first 10 pending-triage issues

2. **AI Categorizes Issues**
   - Each issue is analyzed by AI
   - Category, confidence, reasoning, and other metadata are generated

3. **Results Saved to Markdown**
   - `TriageReportService.saveTriageResults()` is called
   - Results are formatted and appended to `triage-report.md`
   - File is created if it doesn't exist

4. **File Location**
   - Report is saved in the project root directory
   - File name: `triage-report.md`

## Benefits

✅ **Persistent History**
- All triage sessions are preserved
- Easy to review past categorizations

✅ **Human-Readable Format**
- Markdown is easy to read and edit
- Can be viewed in any text editor or GitHub

✅ **Comprehensive Documentation**
- Includes all relevant details about each issue
- AI reasoning provides context for decisions

✅ **Easy Sharing**
- Can be committed to version control
- Can be shared with team members
- Can be converted to other formats (PDF, HTML)

✅ **Audit Trail**
- Timestamps for each session
- Shows who/what was categorized and when

## File Management

- **Append-Only:** New sessions are appended, old data is preserved
- **No Size Limit:** File grows with each session (consider archiving periodically)
- **Manual Cleanup:** Delete or archive the file manually if needed
- **Version Control:** Can be added to `.gitignore` if desired

## Future Enhancements

Potential improvements:
- Export to different formats (JSON, CSV, HTML)
- Configurable report location
- Automatic archiving after X sessions
- Summary statistics across all sessions
- Search/filter functionality
- Integration with project documentation

## Technical Details

- **Service:** Spring `@Service` component
- **File I/O:** Uses Java NIO `Files` API
- **Format:** Standard Markdown with GitHub-flavored extensions
- **Encoding:** UTF-8
- **Error Handling:** Logs errors but doesn't fail the categorization process
