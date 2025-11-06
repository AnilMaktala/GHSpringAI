const API_BASE = '/api/triage';

// State management
let issues = [];
let triageResults = new Map();
let currentOffset = 0;
let totalIssues = 0;
let hasMore = false;
const PAGE_SIZE = 10;

// DOM elements
const refreshBtn = document.getElementById('refreshBtn');
const buildKbBtn = document.getElementById('buildKbBtn');
const kbStatusBtn = document.getElementById('kbStatusBtn');
const issuesList = document.getElementById('issuesList');
const loading = document.getElementById('loading');
const issueCount = document.getElementById('issueCount');
const kbStatus = document.getElementById('kbStatus');
const statisticsPanel = document.getElementById('statistics');
const pagination = document.getElementById('pagination');
const prevBtn = document.getElementById('prevBtn');
const nextBtn = document.getElementById('nextBtn');
const pageInfo = document.getElementById('pageInfo');
const modelSelect = document.getElementById('modelSelect');

// Load saved model preference
const savedModel = localStorage.getItem('selectedModel');
if (savedModel) {
    modelSelect.value = savedModel;
}

// Event listeners
refreshBtn.addEventListener('click', () => loadIssues(0));
buildKbBtn.addEventListener('click', buildKnowledgeBase);
kbStatusBtn.addEventListener('click', toggleKbStatus);
prevBtn.addEventListener('click', () => loadIssues(currentOffset - PAGE_SIZE));
nextBtn.addEventListener('click', () => loadIssues(currentOffset + PAGE_SIZE));
modelSelect.addEventListener('change', (e) => {
    localStorage.setItem('selectedModel', e.target.value);
    console.log('Model changed to:', e.target.value);
});

// Initialize
loadIssues(0);
loadKbStatus();
loadStatistics();

async function loadIssues(offset = 0) {
    showLoading(true);
    try {
        const response = await fetch(`${API_BASE}/issues?limit=${PAGE_SIZE}&offset=${offset}`);
        if (!response.ok) throw new Error('Failed to fetch issues');
        
        const data = await response.json();
        issues = data.issues;
        totalIssues = data.total;
        currentOffset = data.offset;
        hasMore = data.hasMore;
        
        renderIssues();
        updateIssueCount();
        updatePagination();
    } catch (error) {
        console.error('Error loading issues:', error);
        showError('Failed to load issues. Please try again.');
    } finally {
        showLoading(false);
    }
}

async function triageIssue(issueNumber, button) {
    const originalText = button.innerHTML;
    button.disabled = true;
    button.innerHTML = '<span class="spinner" style="width: 16px; height: 16px; border-width: 2px;"></span> Triaging...';
    
    const selectedModel = modelSelect.value;
    
    try {
        const response = await fetch(`${API_BASE}/classify/${issueNumber}?model=${encodeURIComponent(selectedModel)}`, {
            method: 'POST'
        });
        
        if (!response.ok) throw new Error('Failed to classify issue');
        
        const result = await response.json();
        triageResults.set(issueNumber, result);
        displayTriageResult(issueNumber, result);
    } catch (error) {
        console.error('Error triaging issue:', error);
        alert('Failed to triage issue. Please try again.');
    } finally {
        button.disabled = false;
        button.innerHTML = originalText;
    }
}

function renderIssues() {
    if (issues.length === 0) {
        issuesList.innerHTML = `
            <div class="empty-state">
                <h2>🎉 No pending issues!</h2>
                <p>All issues have been triaged.</p>
            </div>
        `;
        return;
    }
    
    issuesList.innerHTML = issues.map(issue => `
        <div class="issue-card" id="issue-${issue.number}">
            <div class="issue-header">
                <div class="issue-title">
                    <div class="issue-number">
                        <a href="${issue.html_url}" target="_blank" rel="noopener noreferrer">#${issue.number}</a>
                    </div>
                    <h3>${escapeHtml(issue.title)}</h3>
                </div>
            </div>
            
            <div class="issue-meta">
                <span>👤 ${escapeHtml(issue.user.login)}</span>
                <span>📅 ${formatDate(issue.createdAt)}</span>
                ${issue.labels.length > 0 ? `<span>🏷️ ${issue.labels.length} labels</span>` : ''}
            </div>
            
            ${issue.body ? `
                <div class="issue-body">
                    ${escapeHtml(truncate(issue.body, 200))}
                </div>
            ` : ''}
            
            <div class="issue-actions">
                <button class="btn btn-triage" onclick="triageIssue(${issue.number}, this)">
                    <span class="icon">🔍</span> Triage Issue
                </button>
            </div>
            
            <div id="result-${issue.number}"></div>
        </div>
    `).join('');
}

function displayTriageResult(issueNumber, result) {
    const resultContainer = document.getElementById(`result-${issueNumber}`);
    
    const categoryIcons = {
        'BUG': '🐛',
        'FEATURE_REQUEST': '✨',
        'QUESTION': '❓',
        'USABILITY': '🎨',
        'DUPLICATE': '📋'
    };
    
    resultContainer.innerHTML = `
        <div class="triage-result show">
            <div class="result-header">
                <span class="icon">${categoryIcons[result.category] || '📌'}</span>
                <span class="category-badge category-${result.category}">
                    ${result.category.replace('_', ' ')}
                </span>
                <span class="confidence-score">
                    Confidence: ${result.confidence}%
                </span>
            </div>
            
            ${result.reasoning ? `
                <div class="result-section">
                    <h4>💡 Reasoning</h4>
                    <p>${escapeHtml(result.reasoning)}</p>
                </div>
            ` : ''}
            
            ${result.isDuplicate ? `
                <div class="result-section duplicate-warning">
                    <h4>⚠️ Possible Duplicate</h4>
                    <div class="duplicate-info">
                        ${result.duplicateOf && result.duplicateOf.length > 0 ? `
                            <p class="duplicate-issues">
                                May be duplicate of: ${result.duplicateOf.map(num => 
                                    `<a href="https://github.com/kirodotdev/Kiro/issues/${num}" target="_blank">#${num}</a>`
                                ).join(', ')}
                            </p>
                        ` : ''}
                        ${result.duplicateReasoning ? `
                            <p class="duplicate-reasoning">${escapeHtml(result.duplicateReasoning)}</p>
                        ` : ''}
                    </div>
                </div>
            ` : ''}
            
            ${result.reproducibility ? `
                <div class="result-section">
                    <h4>🔄 Reproducibility</h4>
                    <div class="reproducibility-info">
                        <span class="reproducibility-badge reproducibility-${result.reproducibility.toLowerCase()}">
                            ${result.reproducibility}
                        </span>
                        ${result.reproducibilityNotes ? `
                            <p class="reproducibility-notes">${escapeHtml(result.reproducibilityNotes)}</p>
                        ` : ''}
                    </div>
                </div>
            ` : ''}
            
            ${result.suggestedResponse ? `
                <div class="result-section">
                    <h4>💬 Suggested Response</h4>
                    <div class="suggested-response">
                        ${escapeHtml(result.suggestedResponse)}
                    </div>
                </div>
            ` : ''}
            
            ${result.suggestedLabels && result.suggestedLabels.length > 0 ? `
                <div class="result-section">
                    <h4>🏷️ Suggested Labels</h4>
                    <div class="label-badges">
                        ${result.suggestedLabels.map(label => `
                            <span class="label-badge">${escapeHtml(label)}</span>
                        `).join('')}
                    </div>
                </div>
            ` : ''}
        </div>
    `;
}

function updateIssueCount() {
    issueCount.textContent = `Showing ${currentOffset + 1}-${currentOffset + issues.length} of ${totalIssues} issues`;
}

function updatePagination() {
    if (totalIssues <= PAGE_SIZE) {
        pagination.style.display = 'none';
        return;
    }
    
    pagination.style.display = 'flex';
    prevBtn.disabled = currentOffset === 0;
    nextBtn.disabled = !hasMore;
    
    const currentPage = Math.floor(currentOffset / PAGE_SIZE) + 1;
    const totalPages = Math.ceil(totalIssues / PAGE_SIZE);
    pageInfo.textContent = `Page ${currentPage} of ${totalPages}`;
}

async function loadStatistics() {
    try {
        const response = await fetch(`${API_BASE}/statistics`);
        if (!response.ok) return;
        
        const stats = await response.json();
        displayStatistics(stats);
    } catch (error) {
        console.error('Error loading statistics:', error);
    }
}

function displayStatistics(stats) {
    const byCategory = stats.byCategory || {};
    
    statisticsPanel.innerHTML = `
        <div class="stats-header">
            <h3>📊 Triage Statistics</h3>
            <span class="total-badge">${stats.totalPending} Total Pending</span>
        </div>
        <div class="stats-grid">
            <div class="stat-card stat-bug">
                <div class="stat-icon">🐛</div>
                <div class="stat-value">${byCategory.bug || 0}</div>
                <div class="stat-label">Bugs</div>
            </div>
            <div class="stat-card stat-feature">
                <div class="stat-icon">✨</div>
                <div class="stat-value">${byCategory.feature || 0}</div>
                <div class="stat-label">Features</div>
            </div>
            <div class="stat-card stat-question">
                <div class="stat-icon">❓</div>
                <div class="stat-value">${byCategory.question || 0}</div>
                <div class="stat-label">Questions</div>
            </div>
            <div class="stat-card stat-usability">
                <div class="stat-icon">🎨</div>
                <div class="stat-value">${byCategory.usability || 0}</div>
                <div class="stat-label">Usability</div>
            </div>
            <div class="stat-card stat-unlabeled">
                <div class="stat-icon">🏷️</div>
                <div class="stat-value">${byCategory.unlabeled || 0}</div>
                <div class="stat-label">Unlabeled</div>
            </div>
        </div>
    `;
}

function showLoading(show) {
    loading.style.display = show ? 'block' : 'none';
    issuesList.style.display = show ? 'none' : 'block';
}

function showError(message) {
    issuesList.innerHTML = `
        <div class="empty-state">
            <h2>⚠️ Error</h2>
            <p>${escapeHtml(message)}</p>
        </div>
    `;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function truncate(text, length) {
    if (text.length <= length) return text;
    return text.substring(0, length) + '...';
}

function formatDate(dateString) {
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now - date;
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
    
    if (diffDays === 0) return 'Today';
    if (diffDays === 1) return 'Yesterday';
    if (diffDays < 7) return `${diffDays} days ago`;
    
    return date.toLocaleDateString();
}

async function buildKnowledgeBase() {
    if (!confirm('Building the knowledge base will analyze up to 2000 triaged issues (open and closed, excluding pending-triage). This may take several minutes. Continue?')) {
        return;
    }
    
    const originalText = buildKbBtn.innerHTML;
    buildKbBtn.disabled = true;
    buildKbBtn.innerHTML = '<span class="spinner" style="width: 16px; height: 16px; border-width: 2px;"></span> Building...';
    
    try {
        const response = await fetch(`${API_BASE}/knowledge-base/build?maxIssues=2000`, {
            method: 'POST'
        });
        
        if (!response.ok) throw new Error('Failed to build knowledge base');
        
        const result = await response.json();
        alert(`Knowledge base built successfully!\n\nTotal issues analyzed: ${result.totalIssuesAnalyzed}`);
        loadKbStatus();
    } catch (error) {
        console.error('Error building knowledge base:', error);
        alert('Failed to build knowledge base. Please try again.');
    } finally {
        buildKbBtn.disabled = false;
        buildKbBtn.innerHTML = originalText;
    }
}

async function loadKbStatus() {
    try {
        const response = await fetch(`${API_BASE}/knowledge-base/status`);
        if (!response.ok) return;
        
        const status = await response.json();
        
        if (status.exists && status.totalIssuesAnalyzed > 0) {
            buildKbBtn.innerHTML = '<span class="icon">🧠</span> Rebuild KB';
        }
    } catch (error) {
        console.error('Error loading KB status:', error);
    }
}

async function toggleKbStatus() {
    if (kbStatus.style.display === 'none') {
        try {
            const response = await fetch(`${API_BASE}/knowledge-base/status`);
            if (!response.ok) throw new Error('Failed to fetch KB status');
            
            const status = await response.json();
            displayKbStatus(status);
            kbStatus.style.display = 'block';
        } catch (error) {
            console.error('Error fetching KB status:', error);
            alert('Failed to load knowledge base status');
        }
    } else {
        kbStatus.style.display = 'none';
    }
}

function displayKbStatus(status) {
    if (!status.exists || status.totalIssuesAnalyzed === 0) {
        kbStatus.innerHTML = `
            <h3>📊 Knowledge Base Status</h3>
            <p>No knowledge base exists yet. Click "Build Knowledge Base" to create one.</p>
        `;
        return;
    }
    
    const distribution = status.categoryDistribution || {};
    
    kbStatus.innerHTML = `
        <h3>📊 Knowledge Base Status</h3>
        <div class="kb-stats">
            <div class="kb-stat">
                <div class="kb-stat-value">${status.totalIssuesAnalyzed}</div>
                <div class="kb-stat-label">Total Issues</div>
            </div>
            <div class="kb-stat">
                <div class="kb-stat-value">${distribution.BUG || 0}</div>
                <div class="kb-stat-label">Bugs</div>
            </div>
            <div class="kb-stat">
                <div class="kb-stat-value">${distribution.FEATURE_REQUEST || 0}</div>
                <div class="kb-stat-label">Features</div>
            </div>
            <div class="kb-stat">
                <div class="kb-stat-value">${distribution.QUESTION || 0}</div>
                <div class="kb-stat-label">Questions</div>
            </div>
            <div class="kb-stat">
                <div class="kb-stat-value">${distribution.USABILITY || 0}</div>
                <div class="kb-stat-label">Usability</div>
            </div>
        </div>
        <p style="margin-top: 16px; color: #718096; font-size: 0.9rem;">
            Last updated: ${formatDate(status.lastUpdated)}
        </p>
    `;
}
