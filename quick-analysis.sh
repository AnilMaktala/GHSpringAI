#!/bin/bash

echo "Analyzing 10 pending-triage issues..."
echo ""

FEATURE=0
BUG=0
QUESTION=0
USABILITY=0

for i in {1..10}; do
    OFFSET=$((i-1))
    ISSUE=$(curl -s "http://localhost:8080/api/triage/issues?limit=1&offset=$OFFSET" | jq -r '.issues[0].number')
    
    if [ -z "$ISSUE" ] || [ "$ISSUE" = "null" ]; then
        break
    fi
    
    echo "Analyzing issue #$ISSUE..."
    CATEGORY=$(curl -s -X POST "http://localhost:8080/api/triage/classify/$ISSUE" | jq -r '.category')
    
    echo "  Category: $CATEGORY"
    
    case $CATEGORY in
        *"Feature"*|*"FEATURE"*)
            ((FEATURE++))
            ;;
        *"Bug"*|*"BUG"*)
            ((BUG++))
            ;;
        *"Question"*|*"QUESTION"*)
            ((QUESTION++))
            ;;
        *"Usability"*|*"USABILITY"*)
            ((USABILITY++))
            ;;
    esac
    
    sleep 15  # Wait between classifications
done

TOTAL=$((FEATURE + BUG + QUESTION + USABILITY))

echo ""
echo "========================================="
echo "Results from $TOTAL issues:"
echo "========================================="
echo "🐛 Bugs: $BUG ($(( BUG * 100 / TOTAL ))%)"
echo "✨ Feature Requests: $FEATURE ($(( FEATURE * 100 / TOTAL ))%)"
echo "❓ Questions: $QUESTION ($(( QUESTION * 100 / TOTAL ))%)"
echo "🎨 Usability: $USABILITY ($(( USABILITY * 100 / TOTAL ))%)"
echo ""
echo "Estimated for all 269 pending issues:"
echo "Bugs: ~$(( BUG * 269 / TOTAL ))"
echo "Feature Requests: ~$(( FEATURE * 269 / TOTAL ))"
echo "Questions: ~$(( QUESTION * 269 / TOTAL ))"
echo "Usability: ~$(( USABILITY * 269 / TOTAL ))"
