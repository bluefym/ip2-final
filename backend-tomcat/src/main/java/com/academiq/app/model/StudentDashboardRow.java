package com.academiq.app.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class StudentDashboardRow {
    @JsonProperty("student_id")
    private Long studentId;

    @JsonProperty("roll_number")
    private String rollNumber;

    @JsonProperty("student_name")
    private String studentName;

    private Double cgpa;

    @JsonProperty("attendance_pct")
    private Double attendancePct;

    @JsonProperty("assignment_marks")
    private Double assignmentMarks;

    @JsonProperty("class_behavior")
    private Integer classBehavior;

    @JsonProperty("lab_behavior")
    private Integer labBehavior;

    @JsonProperty("prediction_id")
    private Long predictionId;

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("risk_score")
    private Double riskScore;

    private String summary;

    private List<PredictionSuggestion> suggestions = new ArrayList<>();

    @JsonProperty("predicted_at")
    private String predictedAt;

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public void setRollNumber(String rollNumber) {
        this.rollNumber = rollNumber;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public Double getCgpa() {
        return cgpa;
    }

    public void setCgpa(Double cgpa) {
        this.cgpa = cgpa;
    }

    public Double getAttendancePct() {
        return attendancePct;
    }

    public void setAttendancePct(Double attendancePct) {
        this.attendancePct = attendancePct;
    }

    public Double getAssignmentMarks() {
        return assignmentMarks;
    }

    public void setAssignmentMarks(Double assignmentMarks) {
        this.assignmentMarks = assignmentMarks;
    }

    public Integer getClassBehavior() {
        return classBehavior;
    }

    public void setClassBehavior(Integer classBehavior) {
        this.classBehavior = classBehavior;
    }

    public Integer getLabBehavior() {
        return labBehavior;
    }

    public void setLabBehavior(Integer labBehavior) {
        this.labBehavior = labBehavior;
    }

    public Long getPredictionId() {
        return predictionId;
    }

    public void setPredictionId(Long predictionId) {
        this.predictionId = predictionId;
    }

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

    public String getPredictedAt() {
        return predictedAt;
    }

    public void setPredictedAt(String predictedAt) {
        this.predictedAt = predictedAt;
    }
}
