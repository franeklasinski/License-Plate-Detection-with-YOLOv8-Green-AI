package com.example.myapplication;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_VIDEO = 1;
    private static final int PICK_IMAGE = 2;
    private static final long FRAME_INTERVAL_MS = 100; // Zmniejszone z 1000ms do 500ms dla płynniejszego wyświetlania

    private VideoView videoView;
    private ImageView imageView;
    private DrawBoxes drawBoxes;
    private DrawBoxes imageDrawBoxes;
    private YoloV8 yoloV8;
    private TextView statusText, detectionsListText;
    private Button detectButton, playButton, pauseButton, selectImageButton, detectImageButton;
    private Uri selectedVideoUri;
    private Bitmap selectedImageBitmap;
    private MediaMetadataRetriever retriever;
    private boolean isProcessed = false;
    private List<List<DetectionResult>> frameDetections = new ArrayList<>();
    private VideoProcessor videoProcessor;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int videoWidth, videoHeight;
    private MaterialCardView videoCard, statsCard, imageCard;
    private TextRecognizer textRecognizer;
    private LinearLayout videoSection, imageSection;
    private RadioGroup modeRadioGroup;
    private boolean isVideoMode = true;
    private FrameLayout videoContainer, imageContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        initializeComponents();
        setupListeners();
        setupVideoView();
        updateModeVisibility();
    }

    private void initializeViews() {
        videoView = findViewById(R.id.videoView);
        imageView = findViewById(R.id.imageView);
        drawBoxes = findViewById(R.id.overlayView);
        imageDrawBoxes = findViewById(R.id.imageOverlayView);
        detectButton = findViewById(R.id.detectButton);
        playButton = findViewById(R.id.playButton);
        pauseButton = findViewById(R.id.pauseButton);
        selectImageButton = findViewById(R.id.selectImageButton);
        detectImageButton = findViewById(R.id.detectImageButton);
        statusText = findViewById(R.id.statusText);
        detectionsListText = findViewById(R.id.detectionsListText);
        videoCard = findViewById(R.id.videoCard);
        statsCard = findViewById(R.id.statsCard);
        imageCard = findViewById(R.id.imageCard);
        videoSection = findViewById(R.id.videoSection);
        imageSection = findViewById(R.id.imageSection);
        modeRadioGroup = findViewById(R.id.modeRadioGroup);
        videoContainer = findViewById(R.id.videoContainer);
        imageContainer = findViewById(R.id.imageContainer);
    }

    private void initializeComponents() {
        yoloV8 = new YoloV8(this);
        retriever = new MediaMetadataRetriever();
        videoProcessor = new VideoProcessor();
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        detectButton.setEnabled(false);
        playButton.setEnabled(false);
        pauseButton.setEnabled(false);
        detectImageButton.setEnabled(false);

        statusText.setText("Wybierz tryb wykrywania");
    }

    private void setupListeners() {
        Button selectVideoButton = findViewById(R.id.selectVideoButton);
        Button clearButton = findViewById(R.id.clearButton);

        modeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            isVideoMode = (checkedId == R.id.radioVideo);
            updateModeVisibility();
        });

        selectVideoButton.setOnClickListener(v -> selectVideo());
        selectImageButton.setOnClickListener(v -> selectImage());
        detectButton.setOnClickListener(v -> startVideoDetection());
        detectImageButton.setOnClickListener(v -> startImageDetection());
        playButton.setOnClickListener(v -> playVideo());
        pauseButton.setOnClickListener(v -> pauseVideo());
        clearButton.setOnClickListener(v -> reset());
    }

    private void updateModeVisibility() {
        if (isVideoMode) {
            videoSection.setVisibility(View.VISIBLE);
            imageSection.setVisibility(View.GONE);
            statusText.setText("Wybierz film aby rozpocząć wykrywanie tablic");
        } else {
            videoSection.setVisibility(View.GONE);
            imageSection.setVisibility(View.VISIBLE);
            statusText.setText("Wybierz zdjęcie aby wykryć tablicę rejestracyjną");
        }
    }

    private void selectVideo() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Wybierz film"), PICK_VIDEO);
    }

    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Wybierz zdjęcie"), PICK_IMAGE);
    }

    private void startImageDetection() {
        if (selectedImageBitmap == null) {
            Toast.makeText(this, "Najpierw wybierz zdjęcie!", Toast.LENGTH_SHORT).show();
            return;
        }

        statusText.setText("Wykrywanie tablic...");
        detectImageButton.setEnabled(false);

        new Thread(() -> {
            try {
                List<DetectionResult> detections = yoloV8.detect(selectedImageBitmap);

                runOnUiThread(() -> {
                    if (detections.isEmpty()) {
                        statusText.setText("Nie wykryto żadnych tablic");
                        detectImageButton.setEnabled(true);
                        detectionsListText.setText("Brak detekcji");
                    } else {
                        if (imageView.getWidth() == 0 || imageView.getHeight() == 0) {
                            imageView.post(() -> setupImageOverlayAndOCR(detections));
                        } else {
                            setupImageOverlayAndOCR(detections);
                        }
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText("Błąd: " + e.getMessage());
                    detectImageButton.setEnabled(true);
                    Toast.makeText(MainActivity.this,
                            "Błąd wykrywania: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
                e.printStackTrace();
            }
        }).start();
    }

    private void setupImageOverlayAndOCR(List<DetectionResult> detections) {
        int viewW = imageView.getWidth();
        int viewH = imageView.getHeight();

        if (viewW == 0 || viewH == 0) {
            statusText.setText("Błąd pomiaru widoku");
            detectImageButton.setEnabled(true);
            return;
        }

        int imgW = selectedImageBitmap.getWidth();
        int imgH = selectedImageBitmap.getHeight();

        float imgAspect = (float) imgW / imgH;
        float viewAspect = (float) viewW / viewH;

        int dispW, dispH, offX = 0, offY = 0;

        if (imgAspect > viewAspect) {
            dispW = viewW;
            dispH = (int) (viewW / imgAspect);
            offY = (viewH - dispH) / 2;
        } else {
            dispH = viewH;
            dispW = (int) (viewH * imgAspect);
            offX = (viewW - dispW) / 2;
        }

        imageDrawBoxes.setVideoOriginalSize(imgW, imgH);
        imageDrawBoxes.setVideoDisplayInfo(dispW, dispH, offX, offY);
        imageDrawBoxes.setDetections(detections);

        statusText.setText(String.format("Wykryto %d tablice(y). Rozpoczynam OCR...",
                detections.size()));

        performOCR(detections);
    }

    private void performOCR(List<DetectionResult> detections) {
        StringBuilder results = new StringBuilder();
        results.append("Wykryte tablice:\n\n");

        // Przetwarzaj każdą tablicę osobno
        processNextPlate(detections, 0, results);
    }

    private void processNextPlate(List<DetectionResult> detections, int index, StringBuilder results) {
        if (index >= detections.size()) {
            // Wszystkie tablice przetworzone
            detectionsListText.setText(results.toString());
            statusText.setText("Zakończono rozpoznawanie");
            detectImageButton.setEnabled(true);
            return;
        }

        DetectionResult detection = detections.get(index);

        // Wytnij region tablicy z obrazu
        Bitmap plateBitmap = cropPlateRegion(selectedImageBitmap, detection.getBoundingBox());

        if (plateBitmap == null) {
            results.append(String.format("Tablica %d:\n", index + 1));
            results.append(String.format("Pewność: %.1f%%\n", detection.getConfidence() * 100));
            results.append("Numer: błąd wycinania\n\n");
            processNextPlate(detections, index + 1, results);
            return;
        }

        InputImage image = InputImage.fromBitmap(plateBitmap, 0);

        textRecognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String plateNumber = extractPlateNumber(visionText.getText(), detection);

                    results.append(String.format("Tablica %d:\n", index + 1));
                    results.append(String.format("Pewność: %.1f%%\n", detection.getConfidence() * 100));

                    if (plateNumber != null && !plateNumber.isEmpty()) {
                        results.append(String.format("Numer: %s\n", plateNumber));
                    } else {
                        results.append("Numer: nie rozpoznano\n");
                    }
                    results.append("\n");

                    // Aktualizuj status dla użytkownika
                    detectionsListText.setText(results.toString());
                    statusText.setText(String.format("Przetwarzanie %d/%d...", index + 1, detections.size()));

                    plateBitmap.recycle();

                    // Przetwórz następną tablicę
                    processNextPlate(detections, index + 1, results);
                })
                .addOnFailureListener(e -> {
                    results.append(String.format("Tablica %d:\n", index + 1));
                    results.append(String.format("Pewność: %.1f%%\n", detection.getConfidence() * 100));
                    results.append("Numer: błąd OCR\n\n");

                    plateBitmap.recycle();

                    // Przetwórz następną tablicę
                    processNextPlate(detections, index + 1, results);
                });
    }

    private Bitmap cropPlateRegion(Bitmap source, RectF boundingBox) {
        try {
            int left = Math.max(0, (int) boundingBox.left);
            int top = Math.max(0, (int) boundingBox.top);
            int right = Math.min(source.getWidth(), (int) boundingBox.right);
            int bottom = Math.min(source.getHeight(), (int) boundingBox.bottom);

            int width = right - left;
            int height = bottom - top;

            if (width <= 0 || height <= 0) {
                return null;
            }

            // Dodaj margines wokół tablicy (5% z każdej strony) dla lepszego OCR
            int marginX = (int) (width * 0.05);
            int marginY = (int) (height * 0.05);

            left = Math.max(0, left - marginX);
            top = Math.max(0, top - marginY);
            right = Math.min(source.getWidth(), right + marginX);
            bottom = Math.min(source.getHeight(), bottom + marginY);

            width = right - left;
            height = bottom - top;

            return Bitmap.createBitmap(source, left, top, width, height);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String extractPlateNumber(String fullText, DetectionResult detection) {
        Pattern polishPlate = Pattern.compile("[A-Z]{2,3}\\s?[0-9A-Z]{4,5}");

        String[] lines = fullText.split("\n");
        for (String line : lines) {
            String cleanLine = line.replaceAll("[^A-Z0-9\\s]", "").trim();
            if (polishPlate.matcher(cleanLine).find()) {
                return cleanLine.replaceAll("\\s+", " ");
            }
        }

        String bestMatch = "";
        for (String line : lines) {
            String cleanLine = line.replaceAll("[^A-Z0-9]", "").trim();
            if (cleanLine.length() > bestMatch.length() && cleanLine.matches(".*[0-9].*") && cleanLine.matches(".*[A-Z].*")) {
                bestMatch = cleanLine;
            }
        }

        return bestMatch.isEmpty() ? null : bestMatch;
    }

    private void playVideo() {
        if (isProcessed && selectedVideoUri != null) {
            videoView.setVideoURI(selectedVideoUri);
            videoView.setOnPreparedListener(mp -> {
                videoWidth = mp.getVideoWidth();
                videoHeight = mp.getVideoHeight();
                setVideoContainerHeight();

                // Poczekaj aż kontener ustawi swoją wysokość
                videoContainer.post(() -> {
                    updateVideoScale();
                    videoView.start();
                    videoProcessor.startOverlay();
                });
            });
        }
    }

    private void pauseVideo() {
        videoView.pause();
        videoProcessor.stopOverlay();
    }

    private void setupVideoView() {
        videoView.setOnCompletionListener(mp -> {
            videoProcessor.stopOverlay();
            statusText.setText("Film zakończony");
        });

        videoView.setOnPreparedListener(mp -> {
            // Pobierz rzeczywiste wymiary wideo
            videoWidth = mp.getVideoWidth();
            videoHeight = mp.getVideoHeight();
            setVideoContainerHeight();
            updateVideoScale();
        });
    }

    private void updateVideoScale() {
        if (videoWidth == 0 || videoHeight == 0) return;

        videoContainer.post(() -> {
            int viewW = videoView.getWidth();
            int viewH = videoView.getHeight();

            if (viewW == 0 || viewH == 0) return;

            float videoAspect = (float) videoWidth / videoHeight;
            float viewAspect = (float) viewW / viewH;

            int dispW, dispH;
            int offX = 0, offY = 0;

            if (videoAspect > viewAspect) {
                dispW = viewW;
                dispH = (int) (viewW / videoAspect);
                offY = (viewH - dispH) / 2;
            } else {
                dispH = viewH;
                dispW = (int) (viewH * videoAspect);
                offX = (viewW - dispW) / 2;
            }

            drawBoxes.setVideoOriginalSize(videoWidth, videoHeight);
            drawBoxes.setVideoDisplayInfo(dispW, dispH, offX, offY);
        });
    }

    private void startVideoDetection() {
        if (selectedVideoUri == null) {
            statusText.setText("Najpierw wybierz film!");
            Toast.makeText(this, "Musisz wybrać film przed wykrywaniem!", Toast.LENGTH_SHORT).show();
            return;
        }

        statusText.setText("Przetwarzanie filmu...");
        detectButton.setEnabled(false);
        drawBoxes.clearDetections();
        frameDetections.clear();

        new Thread(() -> processVideo()).start();
    }

    private void processVideo() {
        try {
            retriever.setDataSource(this, selectedVideoUri);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

            if (durationStr == null) {
                showError("Nie można odczytać długości filmu");
                return;
            }

            long duration = Long.parseLong(durationStr);
            long frameIntervalUs = FRAME_INTERVAL_MS * 1000;
            int totalFrames = (int) ((duration + FRAME_INTERVAL_MS - 1) / FRAME_INTERVAL_MS);

            for (long timeUs = 0; timeUs < duration * 1000; timeUs += frameIntervalUs) {
                Bitmap frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);

                if (frame != null) {
                    List<DetectionResult> detections = yoloV8.detect(frame);
                    frameDetections.add(detections);
                    frame.recycle();

                    int currentFrame = (int) (timeUs / frameIntervalUs);
                    int progress = Math.min(100, (int) ((currentFrame * 100.0) / totalFrames));

                    runOnUiThread(() -> statusText.setText("Przetwarzanie: " + progress + "%"));
                } else {
                    frameDetections.add(new ArrayList<>());
                }
            }

            runOnUiThread(this::onDetectionComplete);

        } catch (Exception e) {
            showError("Błąd podczas przetwarzania filmu: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void onDetectionComplete() {
        isProcessed = true;
        statusText.setText("Film przetworzony!");
        detectButton.setEnabled(true);
        playButton.setEnabled(true);
        pauseButton.setEnabled(true);

        if (videoCard != null) videoCard.setVisibility(View.VISIBLE);
        if (statsCard != null) statsCard.setVisibility(View.VISIBLE);

        int totalDetections = frameDetections.stream()
                .mapToInt(List::size)
                .sum();

        detectionsListText.setText(String.format(
                "Łącznie wykryto: %d tablic rejestracyjnych w %d klatkach (co %dms)",
                totalDetections, frameDetections.size(), FRAME_INTERVAL_MS
        ));
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            statusText.setText(message);
            detectButton.setEnabled(true);
            detectImageButton.setEnabled(true);
            detectionsListText.setText("Wystąpił błąd podczas analizy");
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void reset() {
        videoView.stopPlayback();
        drawBoxes.clearDetections();
        imageDrawBoxes.clearDetections();
        selectedVideoUri = null;
        selectedImageBitmap = null;
        frameDetections.clear();
        isProcessed = false;

        detectButton.setEnabled(false);
        playButton.setEnabled(false);
        pauseButton.setEnabled(false);
        detectImageButton.setEnabled(false);

        if (videoCard != null) videoCard.setVisibility(View.GONE);
        if (statsCard != null) statsCard.setVisibility(View.GONE);
        if (imageCard != null) imageCard.setVisibility(View.GONE);

        statusText.setText("Wybierz tryb wykrywania");
        detectionsListText.setText("");

        videoProcessor.stopOverlay();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == PICK_VIDEO) {
                handleVideoSelection(data.getData());
            } else if (requestCode == PICK_IMAGE) {
                handleImageSelection(data.getData());
            }
        } else if (resultCode == RESULT_CANCELED) {
            statusText.setText("Anulowano wybór");
        }
    }

    private void handleVideoSelection(Uri uri) {
        selectedVideoUri = uri;
        if (selectedVideoUri == null) {
            statusText.setText("Niepoprawny film");
            return;
        }

        try {
            retriever.setDataSource(this, selectedVideoUri);

            String widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);

            if (widthStr != null && heightStr != null) {
                videoWidth = Integer.parseInt(widthStr);
                videoHeight = Integer.parseInt(heightStr);

                // Ustaw wysokość kontenera na podstawie proporcji wideo
                setVideoContainerHeight();

                // Poczekaj na ustawienie layoutu przed konfiguracją DrawBoxes
                videoContainer.post(() -> {
                    drawBoxes.setVideoOriginalSize(videoWidth, videoHeight);
                    updateVideoScale();
                });
            }

            if (videoCard != null) videoCard.setVisibility(View.VISIBLE);
            if (statsCard != null) statsCard.setVisibility(View.VISIBLE);

            drawBoxes.clearDetections();
            detectButton.setEnabled(true);
            statusText.setText("Film wybrany - możesz rozpocząć wykrywanie");
            detectionsListText.setText("");
            isProcessed = false;

        } catch (Exception e) {
            statusText.setText("Błąd wczytywania filmu: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setVideoContainerHeight() {
        if (videoWidth == 0 || videoHeight == 0) return;

        videoContainer.post(() -> {
            int containerWidth = videoContainer.getWidth();
            if (containerWidth == 0) {
                // Jeśli width jeszcze nie jest dostępny, spróbuj ponownie
                videoContainer.postDelayed(() -> setVideoContainerHeight(), 100);
                return;
            }

            // Oblicz wysokość na podstawie proporcji wideo
            float videoAspect = (float) videoHeight / videoWidth;
            int calculatedHeight = (int) (containerWidth * videoAspect);

            // Ogranicz maksymalną wysokość do rozsądnej wartości
            int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.7);
            int finalHeight = Math.min(calculatedHeight, maxHeight);

            ViewGroup.LayoutParams params = videoContainer.getLayoutParams();
            if (params.height != finalHeight) {
                params.height = finalHeight;
                videoContainer.setLayoutParams(params);

                // Po zmianie wysokości, zaktualizuj DrawBoxes
                videoContainer.post(() -> updateVideoScale());
            }
        });
    }

    private void handleImageSelection(Uri uri) {
        try {
            selectedImageBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);

            // Ustaw wysokość kontenera obrazu
            setImageContainerHeight();

            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setImageBitmap(selectedImageBitmap);

            imageDrawBoxes.clearDetections();

            if (imageCard != null) imageCard.setVisibility(View.VISIBLE);
            if (statsCard != null) statsCard.setVisibility(View.VISIBLE);

            detectImageButton.setEnabled(true);
            statusText.setText("Zdjęcie wybrane - kliknij 'Wykryj tablice'");
            detectionsListText.setText("");

        } catch (IOException e) {
            statusText.setText("Błąd wczytywania zdjęcia: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setImageContainerHeight() {
        if (selectedImageBitmap == null) return;

        imageContainer.post(() -> {
            int containerWidth = imageContainer.getWidth();
            if (containerWidth == 0) {
                imageContainer.postDelayed(() -> setImageContainerHeight(), 100);
                return;
            }

            int imgW = selectedImageBitmap.getWidth();
            int imgH = selectedImageBitmap.getHeight();

            float imageAspect = (float) imgH / imgW;
            int calculatedHeight = (int) (containerWidth * imageAspect);

            int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.7);
            int finalHeight = Math.min(calculatedHeight, maxHeight);

            ViewGroup.LayoutParams params = imageContainer.getLayoutParams();
            if (params.height != finalHeight) {
                params.height = finalHeight;
                imageContainer.setLayoutParams(params);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (retriever != null) retriever.release();
            if (textRecognizer != null) textRecognizer.close();
        } catch (Exception ignored) {}
        videoProcessor.stopOverlay();
    }

    private class VideoProcessor {
        private Runnable overlayRunnable;
        private boolean isRunning = false;
        private List<DetectionResult> lastDetections = new ArrayList<>();

        void startOverlay() {
            if (isRunning || !isProcessed || frameDetections.isEmpty()) return;

            isRunning = true;
            overlayRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!isRunning || !videoView.isPlaying()) {
                        return;
                    }

                    updateDetectionsWithInterpolation();
                    handler.postDelayed(this, 16); // 60 FPS (~16.67ms)
                }
            };
            handler.post(overlayRunnable);
        }

        private void updateDetectionsWithInterpolation() {
            int currentPosition = videoView.getCurrentPosition();
            int frameIndex = currentPosition / (int) FRAME_INTERVAL_MS;

            // Oblicz ułamkową część dla interpolacji
            float frameTime = currentPosition / (float) FRAME_INTERVAL_MS;
            float fraction = frameTime - frameIndex;

            if (frameIndex >= 0 && frameIndex < frameDetections.size()) {
                List<DetectionResult> currentFrameDetections = frameDetections.get(frameIndex);
                List<DetectionResult> interpolatedDetections;

                // Interpoluj z następną klatką jeśli istnieje
                if (frameIndex + 1 < frameDetections.size() && fraction > 0.01f) {
                    List<DetectionResult> nextFrameDetections = frameDetections.get(frameIndex + 1);
                    interpolatedDetections = BoxInterpolator.interpolate(
                            currentFrameDetections,
                            nextFrameDetections,
                            fraction
                    );
                } else {
                    // Brak następnej klatki lub jesteśmy dokładnie na klatce
                    interpolatedDetections = currentFrameDetections;
                }

                // Aktualizuj tylko jeśli coś się zmieniło
                if (!interpolatedDetections.equals(lastDetections)) {
                    drawBoxes.setDetections(interpolatedDetections);
                    lastDetections = new ArrayList<>(interpolatedDetections);

                    String status = interpolatedDetections.isEmpty()
                            ? String.format("Pozycja: %.1fs, Brak detekcji", currentPosition / 1000.0)
                            : String.format("Pozycja: %.1fs, Tablice: %d", currentPosition / 1000.0, interpolatedDetections.size());
                    statusText.setText(status);
                }
            }
        }

        void stopOverlay() {
            isRunning = false;
            if (overlayRunnable != null) {
                handler.removeCallbacks(overlayRunnable);
            }
            lastDetections.clear();
        }
    }
}
