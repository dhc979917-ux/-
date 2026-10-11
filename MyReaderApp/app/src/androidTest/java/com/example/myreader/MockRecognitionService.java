package com.example.myreader;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import org.json.JSONObject;

// Test APK runs separately from the target app and uses only platform classes.
public class MockRecognitionService extends RecognitionService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int generation = 0;

    @Override protected void onStartListening(Intent intent, Callback listener) {
        final int current = ++generation;
        new Thread(() -> {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream input = new URL("http://127.0.0.1:11434/voice").openStream()) {
                    byte[] buffer = new byte[1024];
                    int size;
                    while ((size = input.read(buffer)) != -1) bytes.write(buffer, 0, size);
                }
                JSONObject fixture = new JSONObject(bytes.toString("UTF-8"));
                handler.post(() -> {
                    if (current != generation) return;
                    try { listener.readyForSpeech(new Bundle()); } catch (Exception ignored) { }
                });
                handler.postDelayed(() -> {
                    if (current != generation) return;
                    try {
                        int error = fixture.optInt("error", 0);
                        if (error != 0) { listener.error(error); return; }
                        listener.endOfSpeech();
                        Bundle result = new Bundle();
                        ArrayList<String> values = new ArrayList<>();
                        values.add(fixture.getString("command"));
                        result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, values);
                        result.putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES,
                            new float[] {(float) fixture.optDouble("confidence", 0.95)});
                        listener.results(result);
                    } catch (Exception ignored) { }
                }, 350);
            } catch (Exception error) {
                try { listener.error(SpeechRecognizer.ERROR_NETWORK); } catch (Exception ignored) { }
            }
        }).start();
    }

    @Override protected void onStopListening(Callback listener) { }
    @Override protected void onCancel(Callback listener) {
        generation++;
        handler.removeCallbacksAndMessages(null);
    }
}
