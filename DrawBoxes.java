package com.example.myapplication;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DrawBoxes extends View {

    private Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<DetectionResult> detections = new ArrayList<>();

    // rozmiar KLATKI (bitmapy!) — poprawiony
    private int frameWidth = 0, frameHeight = 0;

    // rozmiar filmu WYŚWIETLANEGO na ekranie
    private int displayWidth = 0, displayHeight = 0;

    private int offsetX = 0, offsetY = 0;

    public DrawBoxes(Context context, AttributeSet attrs) {
        super(context, attrs);

        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(32);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);

        bgPaint.setColor(Color.BLACK);
        bgPaint.setAlpha(160);
    }

    // zamiast video size → KLATKA z YOLO
    public void setVideoOriginalSize(int w, int h) {
        frameWidth = w;
        frameHeight = h;
        invalidate();
    }

    public void setVideoDisplayInfo(int dispW, int dispH, int offX, int offY) {
        displayWidth = dispW;
        displayHeight = dispH;
        offsetX = offX;
        offsetY = offY;
        invalidate();
    }

    public void setDetections(List<DetectionResult> list) {
        detections = (list != null) ? list : new ArrayList<>();
        invalidate();
    }

    public void clearDetections() {
        detections.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (frameWidth == 0 || frameHeight == 0 || displayWidth == 0 || displayHeight == 0)
            return;

        float scaleX = (float) displayWidth / frameWidth;
        float scaleY = (float) displayHeight / frameHeight;

        canvas.save();
        canvas.translate(offsetX, offsetY);

        for (DetectionResult d : detections) {

            RectF b = d.getBoundingBox();

            float L = b.left * scaleX;
            float T = b.top * scaleY;
            float R = b.right * scaleX;
            float B = b.bottom * scaleY;

            boxPaint.setColor(DamageClasses.getColor(d.getLabel()));
            canvas.drawRect(L, T, R, B, boxPaint);

            String txt = d.getLabelWithConfidence();
            float tw = textPaint.measureText(txt);
            float th = textPaint.getTextSize();

            canvas.drawRect(L, T - th - 10, L + tw + 16, T, bgPaint);
            canvas.drawText(txt, L + 8, T - 6, textPaint);
        }

        canvas.restore();
    }
}
