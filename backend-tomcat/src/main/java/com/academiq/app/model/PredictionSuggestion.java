package com.academiq.app.model;

public class PredictionSuggestion {
    private String area;
    private String issue;
    private String recommendation;

    public PredictionSuggestion() {
    }

    public PredictionSuggestion(String area, String issue, String recommendation) {
        this.area = area;
        this.issue = issue;
        this.recommendation = recommendation;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getIssue() {
        return issue;
    }

    public void setIssue(String issue) {
        this.issue = issue;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }
}
