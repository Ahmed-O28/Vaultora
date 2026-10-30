package com.banking.dto;

public class AutocompleteSuggestion {

    private String term;
    private String category; // "BENEFICIARY" or "BILLER"
    private String referenceId; // masked account number or biller ID
    private String displayText;

    public AutocompleteSuggestion() {
    }

    public AutocompleteSuggestion(String term, String category, String referenceId, String displayText) {
        this.term = term;
        this.category = category;
        this.referenceId = referenceId;
        this.displayText = displayText;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getDisplayText() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }
}
