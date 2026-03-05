package com.academiq.app.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SaveResult {
    @JsonProperty("student_id")
    private final Long studentId;

    @JsonProperty("prediction_id")
    private final Long predictionId;

    public SaveResult(Long studentId, Long predictionId) {
        this.studentId = studentId;
        this.predictionId = predictionId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getPredictionId() {
        return predictionId;
    }
}
