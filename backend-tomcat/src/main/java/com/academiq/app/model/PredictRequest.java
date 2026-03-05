package com.academiq.app.model;

public class PredictRequest extends StudentInput {
    private String engine = "rule";

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }
}
