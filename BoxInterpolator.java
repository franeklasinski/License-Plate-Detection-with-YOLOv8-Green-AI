package com.example.myapplication;

import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;

public class BoxInterpolator {

    private static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    private static float iou(RectF a, RectF b) {
        float interLeft = Math.max(a.left, b.left);
        float interTop = Math.max(a.top, b.top);
        float interRight = Math.min(a.right, b.right);
        float interBottom = Math.min(a.bottom, b.bottom);

        float interArea = Math.max(0, interRight - interLeft) * Math.max(0, interBottom - interTop);
        float areaA = Math.max(0, a.right - a.left) * Math.max(0, a.bottom - a.top);
        float areaB = Math.max(0, b.right - b.left) * Math.max(0, b.bottom - b.top);

        return interArea / (areaA + areaB - interArea + 1e-6f);
    }

    /**
     * Interpoluje pudełka między listami a i b. Jeśli rozmiary się różnią,
     * dla niezparowanych elementów szuka najlepszego dopasowania po IoU.
     */
    public static List<DetectionResult> interpolate(
            List<DetectionResult> a,
            List<DetectionResult> b,
            float factor
    ) {
        List<DetectionResult> out = new ArrayList<>();

        if (a == null || a.isEmpty()) {
            // jeśli a puste - sklonuj b
            if (b != null) {
                for (DetectionResult db : b) {
                    out.add(new DetectionResult(new RectF(db.getBoundingBox()), db.getLabel(), db.getConfidence()));
                }
            }
            return out;
        }
        if (b == null || b.isEmpty()) {
            for (DetectionResult da : a) {
                out.add(new DetectionResult(new RectF(da.getBoundingBox()), da.getLabel(), da.getConfidence()));
            }
            return out;
        }

        int n = Math.min(a.size(), b.size());

        // Szybka interpolacja dla par o tej samej pozycji (najprostsze i szybkie)
        for (int i = 0; i < n; i++) {
            DetectionResult da = a.get(i);
            DetectionResult db = b.get(i);

            RectF ra = da.getBoundingBox();
            RectF rb = db.getBoundingBox();

            RectF r = new RectF(
                    lerp(ra.left,   rb.left,   factor),
                    lerp(ra.top,    rb.top,    factor),
                    lerp(ra.right,  rb.right,  factor),
                    lerp(ra.bottom, rb.bottom, factor)
            );

            // Uwaga: konstruktor DetectionResult(RectF, String, float)
            out.add(new DetectionResult(r, da.getLabel(), lerp(da.getConfidence(), db.getConfidence(), factor)));
        }

        // Jeżeli listy mają różne rozmiary, dołącz pozostałe elementy dopasowując po IoU
        if (a.size() != b.size()) {
            List<Integer> usedB = new ArrayList<>();
            for (int i = 0; i < n; i++) usedB.add(i); // te już użyte

            if (a.size() > b.size()) {
                // dla pozostałych z a - spróbuj dopasować najlepsze z b, albo sklonuj a
                for (int i = n; i < a.size(); i++) {
                    DetectionResult da = a.get(i);
                    int bestIdx = -1;
                    float bestIoU = 0f;
                    for (int j = 0; j < b.size(); j++) {
                        if (usedB.contains(j)) continue;
                        float curIoU = iou(da.getBoundingBox(), b.get(j).getBoundingBox());
                        if (curIoU > bestIoU) {
                            bestIoU = curIoU;
                            bestIdx = j;
                        }
                    }
                    if (bestIdx >= 0 && bestIoU > 0.05f) {
                        // interpoluj z dopasowanym b
                        DetectionResult db = b.get(bestIdx);
                        RectF ra = da.getBoundingBox();
                        RectF rb = db.getBoundingBox();
                        RectF r = new RectF(
                                lerp(ra.left, rb.left, factor),
                                lerp(ra.top, rb.top, factor),
                                lerp(ra.right, rb.right, factor),
                                lerp(ra.bottom, rb.bottom, factor)
                        );
                        out.add(new DetectionResult(r, da.getLabel(), lerp(da.getConfidence(), db.getConfidence(), factor)));
                        usedB.add(bestIdx);
                    } else {
                        // brak odpowiednika — sklonuj a
                        out.add(new DetectionResult(new RectF(da.getBoundingBox()), da.getLabel(), da.getConfidence()));
                    }
                }
            } else {
                // b większe niż a — doklej pozostałe z b (sklonowane)
                for (int i = n; i < b.size(); i++) {
                    DetectionResult db = b.get(i);
                    out.add(new DetectionResult(new RectF(db.getBoundingBox()), db.getLabel(), db.getConfidence()));
                }
            }
        }

        return out;
    }
}



