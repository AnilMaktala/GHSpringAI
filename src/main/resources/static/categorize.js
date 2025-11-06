const API_BASE = '/api/triage';
let currentPage = 0;
let pageSize = 10; // Display 10 per page
let allResults = [];
let filteredResults = [];
let currentFilter = 'all';
let totalIssues = 0;

// Category counts
let categoryCounts = {
    BUG: 0,
    FEATURE_REQUEST: 0,
    QUESTION: 0,
    USABILITY: 0
};

// Initialize page
document.addEventListener('DOMContentLoaded', () => {
    console.log('Categorization page loaded');
    updatePaginationUI();
    // Try to load existing quick triage results
    loadQuickTriageResults();
});

async function startQuickTriage() {
    if (confirm('This will quickly categorize the first 10 pending-triage issues based on title and description only. Continue?')) {
        showLoading(true);
        document.getElementById('progressText').textContent = 'Quick triaging...';
        
        try {
            const response = await fetch(`${API_BASE}/quick-triage?limit=10`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            
            // Convert quick triage results to display format
            allResults = data.results.map(r => ({
                issue: {
                    number: r.issueNumber,
                    title: r.issueTitle,
                    htmlUrl: r.issueUrl,
                    user: { login: r.author },
                    createdAt: r.createdAt,
                    comments: 0
                },
                category: r.category,
                confidence: 0.75, // Default confidence for quick triage
                reasoning: r.reason,
                suggestedLabels: [],
                duplicateOf: null
            }));
            
            // Update counts
            categoryCounts = { BUG: 0, FEATURE_REQUEST: 0, QUESTION: 0, USABILITY: 0 };
            allResults.forEach(result => {
                const cat = result.category === 'FEATURE' ? 'FEATURE_REQUEST' : result.category;
                if (categoryCounts.hasOwnProperty(cat)) {
                    categoryCounts[cat]++;
                }
            });
            
            showLoading(false);
            alert(`Quick triage complete! Processed ${allResults.length} issues.`);
            updateSummary();
            filterByCategory(currentFilter);
        } catch (error) {
            showLoading(false);
            alert('Error during quick triage: ' + error.message);
            console.error('Quick triage error:', error);
        }
    }
}

async function loadQuickTriageResults() {
    try {
        const response = await fetch(`${API_BASE}/quick-triage-results`);
        
        if (!response.ok) {
            return; // Silently fail if no results exist
        }
        
        const data = await response.json();
        
        if (data.results && data.results.length > 0) {
            // Convert quick triage results to display format
            allResults = data.results.map(r => ({
                issue: {
                    number: r.issueNumber,
                    title: r.issueTitle,
                    htmlUrl: r.issueUrl,
                    user: { login: r.author },
                    createdAt: r.createdAt,
                    comments: 0
                },
                category: r.category === 'FEATURE' ? 'FEATURE_REQUEST' : r.category,
                confidence: 0.75,
                reasoning: r.reason,
                suggestedLabels: [],
                duplicateOf: null
            }));
            
            // Update counts
            categoryCounts = { BUG: 0, FEATURE_REQUEST: 0, QUESTION: 0, USABILITY: 0 };
            allResults.forEach(result => {
                if (categoryCounts.hasOwnProperty(result.category)) {
                    categoryCounts[result.category]++;
                }
            });
            
            updateSummary();
            filterByCategory(currentFilter);
        }
    } catch (error) {
        console.log('No previous quick triage results found');
    }
}

async function startCategorization() {
    if (confirm('This will perform full categorization of the first 10 pending-triage issues. This may take several minutes. Continue?')) {
        showLoading(true);
        allResults = [];
        categoryCounts = { BUG: 0, FEATURE_REQUEST: 0, QUESTION: 0, USABILITY: 0 };
        currentPage = 0;
        
        try {
            await categorizeAllPages();
            showLoading(false);
            alert(`Categorization complete! Processed ${allResults.length} issues.`);
            updateSummary();
            filterByCategory(currentFilter);
        } catch (error) {
            showLoading(false);
            alert('Error during categorization: ' + error.message);
            console.error('Categorization error:', error);
        }
    }
}

async function categorizeAllPages() {
    try {
        const response = await fetch(`${API_BASE}/categorize-all`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.results && data.results.length > 0) {
            allResults = data.results;
            
            // Update counts
            data.results.forEach(result => {
                if (categoryCounts.hasOwnProperty(result.category)) {
                    categoryCounts[result.category]++;
                }
            });
            
            totalIssues = data.totalIssues;
            
            // Update progress
            document.getElementById('progressText').textContent = 
                `${allResults.length}/${data.totalIssues}`;
        }
    } catch (error) {
        console.error('Error categorizing issues:', error);
        throw error;
    }
}

function filterByCategory(category) {
    currentFilter = category;
    currentPage = 0;
    
    // Update filter buttons
    document.querySelectorAll('.filter-button').forEach(btn => {
        btn.classList.remove('active');
        if (btn.dataset.category === category) {
            btn.classList.add('active');
        }
    });
    
    // Filter results
    if (category === 'all') {
        filteredResults = [...allResults];
    } else {
        filteredResults = allResults.filter(result => result.category === category);
    }
    
    displayCurrentPage();
}

function displayCurrentPage() {
    const startIndex = currentPage * pageSize;
    const endIndex = Math.min(startIndex + pageSize, filteredResults.length);
    const pageResults = filteredResults.slice(startIndex, endIndex);
    
    const issuesList = document.getElementById('issuesList');
    
    if (pageResults.length === 0) {
        issuesList.innerHTML = `
            <div style="text-align: center; padding: 40px; color: #586069;">
                <p>No categorized issues yet. Click "Start Categorization" to begin.</p>
            </div>
        `;
        updatePaginationUI();
        return;
    }
    
    issuesList.innerHTML = pageResults.map(result => createIssueCard(result)).join('');
    updatePaginationUI();
}

function createIssueCard(result) {
    const categoryClass = `category-${result.category.toLowerCase().replace('_', '-')}`;
    const categoryLabel = formatCategory(result.category);
    const confidencePercent = (result.confidence * 100).toFixed(0);
    const issueNumber = result.issue.number;
    const isLabeled = result.issue.labeled || false;
    
    return `
        <div class="issue-card" id="issue-${issueNumber}">
            <div class="issue-header">
                <a href="${result.issue.htmlUrl}" target="_blank" class="issue-title">
                    ${escapeHtml(result.issue.title)}
                </a>
                <span class="issue-number">#${issueNumber}</span>
            </div>
            
            <div style="display: flex; gap: 12px; align-items: center; margin-top: 8px; flex-wrap: wrap;">
                <span class="category-badge ${categoryClass}">${categoryLabel}</span>
                <span style="font-size: 13px; color: #586069;">
                    Confidence: ${confidencePercent}%
                </span>
                ${isLabeled ? `
                    <span style="font-size: 12px; color: #28a745; font-weight: 600;">
                        ✓ Labels Updated
                    </span>
                ` : `
                    <button class="btn btn-primary" style="padding: 4px 12px; font-size: 12px;" 
                            onclick="updateLabels(${issueNumber}, '${result.category}')">
                        Update Labels
                    </button>
                `}
            </div>
            
            <div class="confidence-bar">
                <div class="confidence-fill" style="width: ${confidencePercent}%"></div>
            </div>
            
            ${result.reasoning ? `
                <div style="margin-top: 12px; padding: 12px; background: #f6f8fa; border-radius: 4px; font-size: 13px;">
                    <strong>Reasoning:</strong> ${escapeHtml(result.reasoning)}
                </div>
            ` : ''}
            
            ${result.duplicateOf ? `
                <div style="margin-top: 8px; padding: 8px; background: #fff3cd; border-left: 3px solid #ffc107; font-size: 13px;">
                    ⚠️ Possible duplicate of issue #${result.duplicateOf}
                </div>
            ` : ''}
            
            ${result.suggestedLabels && result.suggestedLabels.length > 0 ? `
                <div style="margin-top: 8px; font-size: 13px;">
                    <strong>Suggested Labels:</strong> 
                    ${result.suggestedLabels.map(label => 
                        `<span style="display: inline-block; padding: 2px 8px; background: #e1e4e8; border-radius: 10px; margin: 2px;">${escapeHtml(label)}</span>`
                    ).join('')}
                </div>
            ` : ''}
            
            <div class="issue-meta">
                <span>👤 ${escapeHtml(result.issue.user.login)}</span>
                <span>📅 ${new Date(result.issue.createdAt).toLocaleDateString()}</span>
                ${result.issue.comments > 0 ? `<span>💬 ${result.issue.comments} comments</span>` : ''}
            </div>
        </div>
    `;
}

async function updateLabels(issueNumber, category) {
    if (!confirm(`Update labels for issue #${issueNumber}?\n\nThis will:\n- Remove: pending-triage\n- Add: ${getCategoryLabel(category)}`)) {
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE}/update-labels/${issueNumber}?category=${category}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.success) {
            // Update the issue card to show it's been labeled
            const issueCard = document.getElementById(`issue-${issueNumber}`);
            if (issueCard) {
                // Find the result and mark it as labeled
                const result = allResults.find(r => r.issue.number === issueNumber);
                if (result) {
                    result.issue.labeled = true;
                }
                
                // Re-render the current page
                displayCurrentPage();
            }
            
            alert(`Successfully updated labels for issue #${issueNumber}`);
        } else {
            throw new Error(data.error || 'Failed to update labels');
        }
    } catch (error) {
        console.error('Error updating labels:', error);
        alert('Error updating labels: ' + error.message);
    }
}

function getCategoryLabel(category) {
    const labelMap = {
        'BUG': 'type: bug',
        'FEATURE_REQUEST': 'type: feature',
        'QUESTION': 'type: question',
        'USABILITY': 'type: usability'
    };
    return labelMap[category] || 'type: other';
}

function formatCategory(category) {
    const categoryMap = {
        'BUG': 'Bug',
        'FEATURE_REQUEST': 'Feature Request',
        'QUESTION': 'Question',
        'USABILITY': 'Usability'
    };
    return categoryMap[category] || category;
}

function updateSummary() {
    document.getElementById('totalCount').textContent = allResults.length;
    document.getElementById('bugCount').textContent = categoryCounts.BUG || 0;
    document.getElementById('featureCount').textContent = categoryCounts.FEATURE_REQUEST || 0;
    document.getElementById('questionCount').textContent = categoryCounts.QUESTION || 0;
    document.getElementById('usabilityCount').textContent = categoryCounts.USABILITY || 0;
}

function updatePaginationUI() {
    const totalFilteredPages = Math.ceil(filteredResults.length / pageSize);
    const pageInfo = document.getElementById('pageInfo');
    const prevBtn = document.getElementById('prevBtn');
    const nextBtn = document.getElementById('nextBtn');
    
    if (filteredResults.length === 0) {
        pageInfo.textContent = 'No results';
        prevBtn.disabled = true;
        nextBtn.disabled = true;
        return;
    }
    
    const startIndex = currentPage * pageSize + 1;
    const endIndex = Math.min((currentPage + 1) * pageSize, filteredResults.length);
    
    pageInfo.textContent = `Showing ${startIndex}-${endIndex} of ${filteredResults.length}`;
    prevBtn.disabled = currentPage === 0;
    nextBtn.disabled = currentPage >= totalFilteredPages - 1;
}

function previousPage() {
    if (currentPage > 0) {
        currentPage--;
        displayCurrentPage();
    }
}

function nextPage() {
    const totalFilteredPages = Math.ceil(filteredResults.length / pageSize);
    if (currentPage < totalFilteredPages - 1) {
        currentPage++;
        displayCurrentPage();
    }
}

function showLoading(show) {
    document.getElementById('loadingOverlay').style.display = show ? 'flex' : 'none';
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Search functionality
async function searchIssue() {
    const input = document.getElementById('issueIdInput');
    const issueId = parseInt(input.value);
    
    // Clear previous results and errors
    clearSearchError();
    
    // Validate input
    if (!input.value || isNaN(issueId) || issueId <= 0) {
        displaySearchError('Please enter a valid positive issue number');
        return;
    }
    
    // Show loading
    showLoading(true);
    document.getElementById('progressText').textContent = `Searching for issue #${issueId}...`;
    
    try {
        const response = await fetch(`${API_BASE}/search-issue/${issueId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        const data = await response.json();
        
        showLoading(false);
        
        if (!response.ok || !data.success) {
            const errorMessage = getErrorMessage(data.errorCode, data.error, issueId);
            displaySearchError(errorMessage);
            return;
        }
        
        // Display the result
        displaySearchResult(data);
        
    } catch (error) {
        showLoading(false);
        console.error('Search error:', error);
        displaySearchError('Network error. Please check your connection and try again.');
    }
}

function displaySearchResult(data) {
    const resultDiv = document.getElementById('searchResult');
    const issue = data.issue;
    const triage = data.triageResult;
    
    const categoryClass = `category-${triage.category.toLowerCase().replace('_', '-')}`;
    const categoryLabel = formatCategory(triage.category);
    const confidencePercent = triage.confidence;
    
    let html = `
        <div class="search-result-header">
            <a href="${issue.url}" target="_blank" class="search-result-title">
                #${issue.number}: ${escapeHtml(issue.title)}
            </a>
            <span class="category-badge ${categoryClass}">${categoryLabel}</span>
        </div>
        
        <div class="search-result-section">
            <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 8px;">
                <span style="font-size: 14px; font-weight: 600;">Confidence: ${confidencePercent}%</span>
                <div class="confidence-bar" style="flex: 1; max-width: 200px;">
                    <div class="confidence-fill" style="width: ${confidencePercent}%"></div>
                </div>
            </div>
        </div>
        
        <div class="search-result-section">
            <h3>Reasoning</h3>
            <p>${escapeHtml(triage.reasoning)}</p>
        </div>
    `;
    
    if (triage.suggestedLabels && triage.suggestedLabels.length > 0) {
        html += `
            <div class="search-result-section">
                <h3>Suggested Labels</h3>
                <div>
                    ${triage.suggestedLabels.map(label => 
                        `<span class="label-tag">${escapeHtml(label)}</span>`
                    ).join('')}
                </div>
            </div>
        `;
    }
    
    if (triage.responseSuggestion) {
        html += `
            <div class="search-result-section">
                <h3>Response Suggestion</h3>
                <p>${escapeHtml(triage.responseSuggestion)}</p>
            </div>
        `;
    }
    
    if (triage.reproducibility) {
        html += `
            <div class="search-result-section">
                <h3>Reproducibility</h3>
                <p><strong>${triage.reproducibility}</strong></p>
                ${triage.reproducibilityNotes ? `<p style="margin-top: 4px; font-size: 13px; color: #586069;">${escapeHtml(triage.reproducibilityNotes)}</p>` : ''}
            </div>
        `;
    }
    
    if (triage.isDuplicate && triage.duplicateOf && triage.duplicateOf.length > 0) {
        html += `
            <div class="search-result-section">
                <div style="padding: 12px; background: #fff3cd; border-left: 3px solid #ffc107; border-radius: 4px;">
                    <h3 style="margin-top: 0; color: #856404;">⚠️ Possible Duplicate</h3>
                    <p style="color: #856404;">This issue may be a duplicate of: ${triage.duplicateOf.map(id => `#${id}`).join(', ')}</p>
                    ${triage.duplicateReasoning ? `<p style="margin-top: 8px; font-size: 13px; color: #856404;">${escapeHtml(triage.duplicateReasoning)}</p>` : ''}
                </div>
            </div>
        `;
    }
    
    html += `
        <div class="search-actions">
            <a href="${issue.url}" target="_blank" class="btn btn-secondary">
                View on GitHub
            </a>
            <button class="btn btn-primary" onclick="updateLabelsFromSearch(${issue.number}, '${triage.category}')">
                Update Labels
            </button>
        </div>
    `;
    
    resultDiv.innerHTML = html;
    resultDiv.style.display = 'block';
    
    // Scroll to result
    resultDiv.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function displaySearchError(message) {
    const errorDiv = document.getElementById('searchError');
    errorDiv.innerHTML = `<strong>Error:</strong> ${escapeHtml(message)}`;
    errorDiv.style.display = 'block';
    
    // Hide result if showing
    document.getElementById('searchResult').style.display = 'none';
    
    // Scroll to error
    errorDiv.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function clearSearchError() {
    document.getElementById('searchError').style.display = 'none';
}

function clearSearch() {
    document.getElementById('issueIdInput').value = '';
    document.getElementById('searchResult').style.display = 'none';
    clearSearchError();
}

function getErrorMessage(errorCode, errorMessage, issueId) {
    switch (errorCode) {
        case 'ISSUE_NOT_FOUND':
            return `Issue #${issueId} was not found in the repository. Please check the issue number and try again.`;
        case 'INVALID_ISSUE_ID':
            return 'Please enter a valid positive issue number.';
        case 'RATE_LIMIT_EXCEEDED':
            return 'GitHub API rate limit exceeded. Please wait a few minutes and try again.';
        case 'GITHUB_API_ERROR':
            return 'Unable to connect to GitHub. Please check your configuration and try again.';
        case 'CLASSIFICATION_ERROR':
            return 'Failed to classify the issue. Please try again later.';
        default:
            return errorMessage || 'An unexpected error occurred. Please try again.';
    }
}

async function updateLabelsFromSearch(issueNumber, category) {
    if (!confirm(`Update labels for issue #${issueNumber}?\n\nThis will:\n- Remove: pending-triage\n- Add: ${getCategoryLabel(category)}`)) {
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE}/update-labels/${issueNumber}?category=${category}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.success) {
            alert(`Successfully updated labels for issue #${issueNumber}`);
            // Optionally clear the search to encourage searching for another issue
            // clearSearch();
        } else {
            throw new Error(data.error || 'Failed to update labels');
        }
    } catch (error) {
        console.error('Error updating labels:', error);
        alert('Error updating labels: ' + error.message);
    }
}
