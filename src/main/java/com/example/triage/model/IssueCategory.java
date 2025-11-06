package com.example.triage.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum IssueCategory {
    BUG("Bug"),
    FEATURE_REQUEST("Feature Request"),
    USABILITY("Usability"),
    QUESTION("Question");
    
    private final String displayName;
    
    IssueCategory(String displayName) {
        this.displayName = displayName;
    }
    
    @JsonValue
    public String getDisplayName() {
        return displayName;
    }
    
    public static IssueCategory fromString(String value) {
        for (IssueCategory category : IssueCategory.values()) {
            if (category.displayName.equalsIgnoreCase(value) || 
                category.name().equalsIgnoreCase(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown category: " + value);
    }
}
