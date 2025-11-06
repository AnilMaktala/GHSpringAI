# Bulk Issue Categorization Feature

## Overview
Added a new bulk categorization feature that allows you to automatically categorize all pending-triage issues into Bugs, Feature Requests, Usability, and Questions with pagination support (25 issues per page).

## What Was Added

### Backend Changes

#### New API Endpoints in `TriageController.java`:

1. **POST `/api/triage/categorize-all`**
   - Categorizes all pending-triage issues in batches
   - Parameters:
     - `pageSize` (default: 25) - Number of issues to process per page
     - `page` (default: 0) - Current page number
   - Returns categorization results with pagination info

2. **GET `/api/triage/categorized-issues`**
   - Retrieves previously categorized issues (placeholder for future enhancement)
   - Parameters:
     - `pageSize` (default: 25)
     - `page` (default: 0)
     - `category` (optional) - Filter by specific category

### Frontend Changes

#### New Files Created:

1. **`categorize.html`**
   - New dedicated page for bulk categorization
   - Features:
     - Summary cards showing total counts by category
     - Filter buttons to view issues by category (All, Bugs, Features, Questions, Usability)
     - Paginated issue display (25 per page)
     - Loading overlay with progress indicator
     - Issue cards showing:
       - Issue title and number
       - Category badge with color coding
       - Confidence score with visual bar
       - AI reasoning
       - Duplicate detection warnings
       - Suggested labels
       - Issue metadata (author, date, comments)

2. **`categorize.js`**
   - JavaScript logic for the categorization page
   - Features:
     - Automatic pagination through all pending issues
     - Real-time progress tracking
     - Category filtering
     - Summary statistics updates
     - Responsive UI updates

#### Updated Files:

1. **`index.html`**
   - Added navigation link to the new bulk categorization page

## How to Use

1. **Access the Application**
   - Main dashboard: http://localhost:8080
   - Bulk categorization: http://localhost:8080/categorize.html

2. **Start Bulk Categorization**
   - Click the "Bulk Categorization" button on the main dashboard
   - Or navigate directly to the categorization page
   - Click "Start Categorization" button
   - Wait for the process to complete (progress shown in real-time)

3. **View Results**
   - Summary cards show total counts by category
   - Filter by category using the filter buttons
   - Navigate through pages using Previous/Next buttons
   - Each issue card shows:
     - Category classification
     - Confidence score
     - AI reasoning
     - Duplicate warnings (if applicable)
     - Suggested labels

4. **Features**
   - 25 issues displayed per page
   - Color-coded category badges:
     - 🔴 Red: Bugs
     - 🟢 Green: Feature Requests
     - 🔵 Blue: Questions
     - 🟡 Yellow: Usability
   - Confidence visualization with progress bars
   - Direct links to GitHub issues

## Technical Details

- **Pagination**: Client-side pagination after fetching all results
- **API Rate Limiting**: 500ms delay between API calls to avoid overwhelming the server
- **Error Handling**: Graceful error handling with user notifications
- **Responsive Design**: Works on desktop and mobile devices
- **Real-time Updates**: Progress tracking during categorization

## Future Enhancements

- Server-side pagination for better performance with large datasets
- Persistent storage of categorization results
- Export functionality (CSV, JSON)
- Batch label application to GitHub issues
- Historical categorization tracking
- Advanced filtering and sorting options
