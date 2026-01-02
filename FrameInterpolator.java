
package com.example.myapplication;

import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;

public class FrameInterpolator {

    private static final float MIN_IOU_THRESHOLD = 0.05f; // Minimalny IoU aby uznać za tę samą tablicę

    public static List<DetectionResult> interpolate(
            List<DetectionResult> prev,
            List<DetectionResult> next,
            float alpha
    ) {
        List<DetectionResult> out = new ArrayList<>();

        // Jeśli którakolwiek lista jest pusta, zwróć niepustą lub pustą listę
        if (prev == null || prev.isEmpty()) {
            return next != null ? new ArrayList<>(next) : out;
        }
        if (next == null || next.isEmpty()) {
            return new ArrayList<>(prev);
        }

        // Dopasuj detekcje z prev do next używając IoU
        boolean[] usedNext = new boolean[next.size()];

        for (DetectionResult a : prev) {
            int bestMatch = -1;
            float bestIoU = 0f;

            // Znajdź najlepsze dopasowanie w next
            for (int j = 0; j < next.size(); j++) {
                if (usedNext[j]) continue;

                float currentIoU = calculateIoU(a.getBoundingBox(), next.get(j).getBoundingBox());
                if (currentIoU > bestIoU) {
                    bestIoU = currentIoU;
                    bestMatch = j;
                }
            }

            // Jeśli znaleziono dobre dopasowanie, interpoluj
            if (bestMatch >= 0 && bestIoU >= MIN_IOU_THRESHOLD) {
                DetectionResult b = next.get(bestMatch);
                usedNext[bestMatch] = true;

                RectF ra = a.getBoundingBox();
                RectF rb = b.getBoundingBox();

                float left   = lerp(ra.left,   rb.left,   alpha);
                float top    = lerp(ra.top,    rb.top,    alpha);
                float right  = lerp(ra.right,  rb.right,  alpha);
                float bottom = lerp(ra.bottom, rb.bottom, alpha);

                RectF interpolatedBox = new RectF(left, top, right, bottom);

                out.add(new DetectionResult(
                        interpolatedBox,
                        a.getLabel(),
                        lerp(a.getConfidence(), b.getConfidence(), alpha)
                ));
            }
            // Jeśli nie znaleziono dopasowania - NIE dodawaj (ramka znika)
        }

        // Dodaj nowe detekcje z next, które nie zostały dopasowane (nowe tablice)
        for (int j = 0; j < next.size(); j++) {
            if (!usedNext[j]) {
                out.add(next.get(j));
            }
        }

        return out;
    }

    private static float calculateIoU(RectF a, RectF b) {
        float interLeft = Math.max(a.left, b.left);
        float interTop = Math.max(a.top, b.top);
        float interRight = Math.min(a.right, b.right);
        float interBottom = Math.min(a.bottom, b.bottom);

        float interArea = Math.max(0, interRight - interLeft) * Math.max(0, interBottom - interTop);
        float areaA = Math.max(0, a.right - a.left) * Math.max(0, a.bottom - a.top);
        float areaB = Math.max(0, b.right - b.left) * Math.max(0, b.bottom - b.top);

        return interArea / (areaA + areaB - interArea + 1e-6f);
    }

    private static float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }
}
