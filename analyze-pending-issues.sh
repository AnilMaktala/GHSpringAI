#!/bin/bash

echo "Analyzing pending-triage issues..."
echo "This will classify a sample of issues to estimate the distribution."
echo ""

# Fetch first 20 pending issues and classify them
echo "Fetching and classifying 20 sample issues..."

FEATURE_COUNT=0
BUG_COUNT=0
QUESTION_COUNT=0
USABILITY_COUNT=0
TOTAL_ANALYZED=0

# Get list of issue numbers
ISSUES=$(curl -s 'http://localhost:8080/api/triage/issues?limit=20&offset=0' | jq -r '.issues[].number')

for ISSUE_NUM in $ISSUES; do
    echo "Classifying issue #$ISSUE_NUM..."
    
    # Classify the issue
    RESULT=$(curl -s -X POST "http://localhost:8080/api/triage/classify/$ISSUE_NUM")
    
    # Extract category
    CATEGORY=$(echo $RESULT | jq -r '.category')
    
    case $CATEGORY in
        "FEATURE_REQUEST")
            ((FEATURE_COUNT++))
            echo "  → Feature Request"
            ;;
        "BUG")
            ((BUG_COUNT++))
            echo "  → Bug"
            ;;
        "QUESTION")
            ((QUESTION_COUNT++))
            echo "  → Question"
            ;;
        "USABILITY")
            ((USABILITY_COUNT++))
            echo "  → Usability"
            ;;
    esac
    
    ((TOTAL_ANALYZED++))
    
    # Small delay to avoid overwhelming the system
    sleep 2
done

echo ""
echo "========================================="
echo "Analysis Results (Sample of $TOTAL_ANALYZED issues):"
echo "========================================="
echo "Feature Requests: $FEATURE_COUNT ($(( FEATURE_COUNT * 100 / TOTAL_ANALYZED ))%)"
echo "Bugs: $BUG_COUNT ($(( BUG_COUNT * 100 / TOTAL_ANALYZED ))%)"
echo "Questions: $QUESTION_COUNT ($(( QUESTION_COUNT * 100 / TOTAL_ANALYZED ))%)"
echo "Usability: $USABILITY_COUNT ($(( USABILITY_COUNT * 100 / TOTAL_ANALYZED ))%)"
echo ""
echo "Estimated distribution across all 269 pending issues:"
echo "Feature Requests: ~$(( FEATURE_COUNT * 269 / TOTAL_ANALYZED )) issues"
echo "Bugs: ~$(( BUG_COUNT * 269 / TOTAL_ANALYZED )) issues"
echo "Questions: ~$(( QUESTION_COUNT * 269 / TOTAL_ANALYZED )) issues"
echo "Usability: ~$(( USABILITY_COUNT * 269 / TOTAL_ANALYZED )) issues"
