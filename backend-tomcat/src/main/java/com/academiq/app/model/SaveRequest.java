package com.academiq.app.model;

public class SaveRequest {
    private StudentInput student;
    private PredictionResult prediction;

    public StudentInput getStudent() {
        return student;
    }

    public void setStudent(StudentInput student) {
        this.student = student;
    }

    public PredictionResult getPrediction() {
        return prediction;
    }

    public void setPrediction(PredictionResult prediction) {
        this.prediction = prediction;
    }
}
