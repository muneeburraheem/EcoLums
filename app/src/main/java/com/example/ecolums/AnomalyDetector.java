package com.example.ecolums;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * On-device anomaly detector for activity logs.
 *
 * <p>Runs a TFLite autoencoder trained on normal activity data. High reconstruction
 * error indicates the entry looks unusual. An entry is flagged when its score
 * exceeds the threshold stored in {@code anomaly_config.json}.</p>
 *
 * <p>Requires {@code ecolums_anomaly_model.tflite} and {@code anomaly_config.json}
 * in {@code assets/}. Initialised once via {@link EcoLumsApp}.</p>
 */
public class AnomalyDetector {

    private static final String MODEL_ASSET  = "ecolums_anomaly_model.tflite";
    private static final String CONFIG_ASSET = "anomaly_config.json";
    private static final int    NUM_FEATURES = 6;

    private Interpreter interpreter;
    private float[]     mean;
    private float[]     std;
    private float       threshold;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Loads the TFLite autoencoder and normalizer config from assets.
     * If either file is missing, {@link #isAvailable()} returns {@code false}.
     *
     * @param context Any {@link Context}; application context preferred.
     */
    public AnomalyDetector(Context context) {
        try {
            Interpreter tmpInterpreter = new Interpreter(loadModelFile(context));
            loadConfig(context);          // sets mean, std, threshold
            interpreter = tmpInterpreter; // only assign after everything succeeds
        } catch (Throwable e) {
            Log.e("AnomalyDetector", "Failed to load TFLite model: " + e.getClass().getName() + " — " + e.getMessage(), e);
            interpreter = null;
            mean = null;
            std = null;
        }
    }

    private ByteBuffer loadModelFile(Context context) throws IOException {
        try (InputStream is = context.getAssets().open(MODEL_ASSET)) {
            byte[] bytes = is.readAllBytes();
            ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length)
                    .order(ByteOrder.nativeOrder());
            buf.put(bytes);
            buf.rewind();
            return buf;
        }
    }

    private void loadConfig(Context context) throws Exception {
        InputStream is   = context.getAssets().open(CONFIG_ASSET);
        byte[]      buf  = is.readAllBytes();
        is.close();
        JSONObject  json = new JSONObject(new String(buf, StandardCharsets.UTF_8));

        JSONArray meanArr = json.getJSONArray("mean");
        JSONArray stdArr  = json.getJSONArray("std");
        mean      = new float[NUM_FEATURES];
        std       = new float[NUM_FEATURES];
        for (int i = 0; i < NUM_FEATURES; i++) {
            mean[i] = (float) meanArr.getDouble(i);
            std[i]  = (float) stdArr.getDouble(i);
        }
        threshold = (float) json.getDouble("threshold");
    }

    // -------------------------------------------------------------------------
    // Inference
    // -------------------------------------------------------------------------

    /**
     * Computes the anomaly score (reconstruction MSE) for an activity log.
     *
     * <p>Lower = more normal. Compare to {@link #getThreshold()} to decide
     * whether to flag the entry.</p>
     *
     * @param subType    {@link Co2Estimator} SUBTYPE_* constant (0–8).
     * @param quantity   Distance in km, kWh, or waste kg — matches category.
     * @param hourOfDay  0–23.
     * @param dayOfWeek  0=Monday … 6=Sunday.
     * @param month      1–12.
     * @param isWeekend  0.0 or 1.0.
     * @return Reconstruction MSE, or {@link Float#NaN} if model unavailable.
     */
    public float score(int subType, float quantity, int hourOfDay,
                       int dayOfWeek, int month, float isWeekend) {
        if (interpreter == null || mean == null || std == null) return Float.NaN;

        float[] raw = { subType, quantity, hourOfDay, dayOfWeek, month, isWeekend };

        // Model has a Normalization layer baked in — pass raw input directly.
        // The model outputs a normalized reconstruction; compute MSE in that space.
        float[][] input  = { raw };
        float[][] output = new float[1][NUM_FEATURES];
        try {
            interpreter.run(input, output);
        } catch (Throwable e) {
            return Float.NaN;
        }

        // Compute MSE between normalized input and model's normalized reconstruction
        float[] normed = normalize(raw);
        float mse = 0.0f;
        for (int i = 0; i < NUM_FEATURES; i++) {
            float diff = normed[i] - output[0][i];
            mse += diff * diff;
        }
        return mse / NUM_FEATURES;
    }

    /** Returns {@code true} if the given score exceeds the anomaly threshold. */
    public boolean isAnomalous(float score) {
        return !Float.isNaN(score) && score > threshold;
    }

    /** Threshold value loaded from {@code anomaly_config.json}. */
    public float getThreshold() {
        return threshold;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private float[] normalize(float[] raw) {
        float[] out = new float[NUM_FEATURES];
        for (int i = 0; i < NUM_FEATURES; i++) {
            out[i] = (raw[i] - mean[i]) / std[i];
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** @return {@code true} if both model and config loaded successfully. */
    public boolean isAvailable() {
        return interpreter != null && mean != null;
    }

    /** Releases the TFLite interpreter. */
    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }
}
