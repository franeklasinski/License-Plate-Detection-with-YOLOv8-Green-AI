package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class YoloV8 {
    private static final String[] CLASSES = DamageClasses.CLASSES;

    private final Module model;

    public YoloV8(Context ctx) {
        try {
            File file = new File(ctx.getFilesDir(), "modelbest.torchscript");
            if (!file.exists()) {
                InputStream is = ctx.getAssets().open("modelbest.torchscript");
                FileOutputStream os = new FileOutputStream(file);
                byte[] buf = new byte[4096];
                int r;
                while ((r = is.read(buf)) != -1) os.write(buf, 0, r);
                is.close();
                os.close();
            }
            model = Module.load(file.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Błąd ładowania modelu", e);
        }
    }

    private static class LetterboxResult {
        Bitmap bitmap;
        float scale;
        int dx, dy;

        LetterboxResult(Bitmap bitmap, float scale, int dx, int dy) {
            this.bitmap = bitmap;
            this.scale = scale;
            this.dx = dx;
            this.dy = dy;
        }
    }

    private LetterboxResult letterbox(Bitmap src, int targetW, int targetH) {
        float scale = Math.min(targetW / (float) src.getWidth(), targetH / (float) src.getHeight());
        int resizedW = Math.round(src.getWidth() * scale);
        int resizedH = Math.round(src.getHeight() * scale);
        Bitmap resized = Bitmap.createScaledBitmap(src, resizedW, resizedH, true);

        Bitmap output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.BLACK);
        int dx = (targetW - resizedW) / 2;
        int dy = (targetH - resizedH) / 2;
        canvas.drawBitmap(resized, dx, dy, null);

        return new LetterboxResult(output, scale, dx, dy);
    }

    public List<DetectionResult> detect(Bitmap bitmap) {
        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        LetterboxResult lb = letterbox(bitmap, 640, 640);

        float[] input = bitmapToTensor(lb.bitmap);
        Tensor inputTensor = Tensor.fromBlob(input, new long[]{1, 3, 640, 640});

        float[] output = model.forward(IValue.from(inputTensor)).toTensor().getDataAsFloatArray();
        List<DetectionResult> detections = parseOutput(output, lb, bitmap.getWidth(), bitmap.getHeight());

        return NMS(detections, 0.3f);
    }

    private float[] bitmapToTensor(Bitmap bitmap) {
        float[] input = new float[3 * 640 * 640];
        int[] pixels = new int[640 * 640];
        bitmap.getPixels(pixels, 0, 640, 0, 0, 640, 640);

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            // R
            input[i] = Color.red(pixel) / 255.0f;
            // G
            input[i + 640 * 640] = Color.green(pixel) / 255.0f;
            // B
            input[i + 2 * 640 * 640] = Color.blue(pixel) / 255.0f;
        }
        return input;
    }

    private List<DetectionResult> parseOutput(float[] output, LetterboxResult lb, int origW, int origH) {
        List<DetectionResult> detections = new ArrayList<>();
        int preds = output.length / 5;
        // Teraz output ma: x, y, w, h, score (tylko 1 klasa)

        for (int i = 0; i < preds; i++) {
            float x = output[i];
            float y = output[preds + i];
            float w = output[2 * preds + i];
            float h = output[3 * preds + i];

            // Dla jednej klasy jest tylko 1 score
            float score = sigmoid(output[4 * preds + i]);

            if (score < 0.55f) continue;

            RectF box = convertToOriginal(x, y, w, h, lb, origW, origH);
            detections.add(new DetectionResult(box, CLASSES[0], score));
        }
        return detections;
    }

    private RectF convertToOriginal(float x, float y, float w, float h, LetterboxResult lb, int origW, int origH) {
        float left = (x - w / 2 - lb.dx) / lb.scale;
        float top = (y - h / 2 - lb.dy) / lb.scale;
        float right = (x + w / 2 - lb.dx) / lb.scale;
        float bottom = (y + h / 2 - lb.dy) / lb.scale;

        return new RectF(
                Math.max(0, Math.min(left, origW)),
                Math.max(0, Math.min(top, origH)),
                Math.max(0, Math.min(right, origW)),
                Math.max(0, Math.min(bottom, origH))
        );
    }

    private float sigmoid(float x) {
        return (float) (1.0 / (1.0 + Math.exp(-x)));
    }

    private List<DetectionResult> NMS(List<DetectionResult> detections, float iouThreshold) {
        List<DetectionResult> results = new ArrayList<>();
        detections.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));

        for (DetectionResult currentDetection : detections) {
            boolean add = true;
            for (DetectionResult existingDetecton : results) {
                if (iou(currentDetection.getBoundingBox(), existingDetecton.getBoundingBox()) > iouThreshold) {
                    add = false;
                    break;
                }
            }
            if (add) results.add(currentDetection);
        }
        return results;
    }

    private float iou(RectF a, RectF b) {
        float interLeft = Math.max(a.left, b.left);
        float interTop = Math.max(a.top, b.top);
        float interRight = Math.min(a.right, b.right);
        float interBottom = Math.min(a.bottom, b.bottom);

        float interArea = Math.max(0, interRight - interLeft) * Math.max(0, interBottom - interTop);
        float areaA = (a.right - a.left) * (a.bottom - a.top);
        float areaB = (b.right - b.left) * (b.bottom - b.top);

        return interArea / (areaA + areaB - interArea + 1e-6f);
    }
}
