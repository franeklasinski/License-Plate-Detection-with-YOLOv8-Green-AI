package com.example.myapplication;

import android.graphics.RectF;

public class DetectionResult {
    private final RectF boundingBox;
    private final String label;
    private final float confidence;

    public DetectionResult(RectF boundingBox, String label, float confidence) {
        this.boundingBox = boundingBox;
        this.label = label;
        this.confidence = confidence;
    }

    public RectF getBoundingBox() {
        return boundingBox;
    }

    public String getLabel() {
        return label;
    }

    public float getConfidence() { // ta metoda była brakująca
        return confidence;
    }

    public String getLabelWithConfidence() {
        return label + " (" + String.format("%.1f", confidence * 100) + "%)";
    }
}

