package com.academiq.app.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class PredictionResult {

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("risk_score")
    private Double riskScore;

    private String summary;

    private List<PredictionSuggestion> suggestions = new ArrayList<>();

    @JsonProperty("engine_used")
    private String engineUsed;

    @JsonProperty("llm_failed")
    private boolean llmFailed;

    @JsonProperty("fallback_reason")
    private String fallbackReason;

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Double riskScore) {
        this.riskScore = riskScore;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<PredictionSuggestion> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<PredictionSuggestion> suggestions) {
        this.suggestions = suggestions;
    }

    public String getEngineUsed() {
        return engineUsed;
    }

    public void setEngineUsed(String engineUsed) {
        this.engineUsed = engineUsed;
    }

    public boolean isLlmFailed() {
        return llmFailed;
    }

    public void setLlmFailed(boolean llmFailed) {
        this.llmFailed = llmFailed;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }
}
