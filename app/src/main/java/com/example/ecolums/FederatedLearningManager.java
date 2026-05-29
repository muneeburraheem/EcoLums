package com.example.ecolums;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles the federated learning loop for CO₂ estimate correction.
 *
 * <p>Stores a rolling window of (modelOutput, staticLabel) pairs locally. Once
 * enough pairs are collected, it fits a linear correction
 * ({@code scale × modelOutput + bias ≈ staticLabel}) and uploads only the two
 * coefficients to Firestore — no raw activity data leaves the device.</p>
 *
 * <p>A Python script ({@code ml/federated/federated_aggregation.py}) averages
 * all per-user corrections and writes a global result to
 * {@code federatedGlobal/correction}, which this class downloads and passes to
 * {@link Co2Estimator}. See FEDERATED_LEARNING.md for the full flow.</p>
 */
public class FederatedLearningManager {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final String PREFS_NAME       = "ecolums_fl";
    private static final String KEY_EXAMPLES     = "fl_examples";
    private static final String KEY_GLOBAL_SCALE = "fl_global_scale";
    private static final String KEY_GLOBAL_BIAS  = "fl_global_bias";
    private static final String KEY_LAST_UPLOAD  = "fl_last_upload";

    /** Minimum local examples before computing and uploading a correction. */
    private static final int   MIN_EXAMPLES      = 8;   // set to 3 for manual testing
    /** Rolling window size — older examples are evicted. */
    private static final int   MAX_EXAMPLES      = 80;
    /** Minimum hours between uploads to avoid hammering Firestore. */
    private static final long  UPLOAD_INTERVAL_H = 6;   // set to 0 for manual testing

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final SharedPreferences prefs;
    private final FirebaseFirestore db;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * @param context Application context used for SharedPreferences.
     */
    public FederatedLearningManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        db    = FirebaseFirestore.getInstance();
    }

    // -------------------------------------------------------------------------
    // Training data collection
    // -------------------------------------------------------------------------

    /**
     * Records a (modelOutput, staticLabel) training pair from a freshly logged activity.
     *
     * <p>Call this every time an activity is submitted, passing the TFLite model's
     * CO₂ estimate and the static-factor CO₂ as the ground-truth label.</p>
     *
     * @param modelOutput CO₂ estimate from the TFLite model (kg).
     * @param staticLabel CO₂ estimate from the static metric factors (kg) — used as label.
     */
    public void recordExample(float modelOutput, float staticLabel) {
        List<float[]> examples = loadExamples();
        examples.add(new float[]{modelOutput, staticLabel});

        // Evict oldest entries if window is full
        while (examples.size() > MAX_EXAMPLES) {
            examples.remove(0);
        }
        saveExamples(examples);
    }

    /** @return Number of training examples stored locally. */
    public int getExampleCount() {
        return loadExamples().size();
    }

    // -------------------------------------------------------------------------
    // Local correction computation
    // -------------------------------------------------------------------------

    /**
     * Solves a 2-parameter least-squares problem to find the best linear correction
     * {@code scale × modelOutput + bias ≈ staticLabel} over all stored examples.
     *
     * <p>Uses the closed-form normal equations — no iterative optimisation required.</p>
     *
     * @return float[2] = {scale, bias}, or null if fewer than {@link #MIN_EXAMPLES} examples.
     */
    public float[] computeLocalCorrection() {
        List<float[]> examples = loadExamples();
        int n = examples.size();
        if (n < MIN_EXAMPLES) return null;

        // Normal equations: [A^T A] [x] = [A^T b]
        // A = [[y_1, 1], [y_2, 1], ...], b = [l_1, l_2, ...]
        // [x] = [scale, bias]
        double sumY2 = 0, sumY = 0, sumYL = 0, sumL = 0;
        for (float[] e : examples) {
            double y = e[0], l = e[1];
            sumY2 += y * y;
            sumY  += y;
            sumYL += y * l;
            sumL  += l;
        }

        double det = sumY2 * n - sumY * sumY;
        if (Math.abs(det) < 1e-12) return null; // degenerate (all outputs identical)

        float scale = (float) ((sumYL * n - sumL * sumY) / det);
        float bias  = (float) ((sumY2 * sumL - sumY * sumYL) / det);
        return new float[]{scale, bias};
    }

    // -------------------------------------------------------------------------
    // Upload
    // -------------------------------------------------------------------------

    /**
     * Computes the local correction and uploads it to Firestore if:
     * <ul>
     *   <li>At least {@link #MIN_EXAMPLES} examples are stored, and</li>
     *   <li>At least {@link #UPLOAD_INTERVAL_H} hours have elapsed since the last upload.</li>
     * </ul>
     *
     * @param userId The authenticated user's UID.
     */
    public void maybeUpload(String userId) {
        if (userId == null || userId.isEmpty()) return;

        long now          = System.currentTimeMillis();
        long lastUpload   = prefs.getLong(KEY_LAST_UPLOAD, 0);
        long elapsedHours = (now - lastUpload) / (1000 * 60 * 60);
        if (elapsedHours < UPLOAD_INTERVAL_H) return;

        float[] correction = computeLocalCorrection();
        if (correction == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("scale",       correction[0]);
        data.put("bias",        correction[1]);
        data.put("sampleCount", loadExamples().size());
        data.put("uploadedAt",  FieldValue.serverTimestamp());

        db.collection("federated")
                .document(userId)
                .set(data)
                .addOnSuccessListener(v ->
                        prefs.edit().putLong(KEY_LAST_UPLOAD, now).apply());
    }

    // -------------------------------------------------------------------------
    // Global correction download
    // -------------------------------------------------------------------------

    /**
     * Downloads the latest FedAvg global correction from Firestore and applies it
     * to the shared {@link Co2Estimator} via {@link EcoLumsApp}.
     *
     * <p>The correction is also cached in SharedPreferences so it survives restarts
     * without requiring another Firestore read.</p>
     */
    public void syncGlobalCorrection() {
        db.collection("federatedGlobal")
                .document("correction")
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    Double rawScale = doc.getDouble("scale");
                    Double rawBias  = doc.getDouble("bias");
                    if (rawScale == null || rawBias == null) return;

                    float scale = rawScale.floatValue();
                    float bias  = rawBias.floatValue();

                    // Persist so we can restore without a network call next launch
                    prefs.edit()
                            .putFloat(KEY_GLOBAL_SCALE, scale)
                            .putFloat(KEY_GLOBAL_BIAS,  bias)
                            .apply();

                    // Push to the live Co2Estimator instance
                    Co2Estimator estimator = EcoLumsApp.getCo2Estimator();
                    if (estimator != null) {
                        estimator.setGlobalCorrection(scale, bias);
                    }
                });
    }

    /**
     * Restores the last-known global correction from SharedPreferences into the
     * {@link Co2Estimator}. Call this at app startup (before the async Firestore
     * fetch completes) so estimates are never uncorrected.
     */
    public void restoreCachedCorrection() {
        float scale = prefs.getFloat(KEY_GLOBAL_SCALE, 1.0f);
        float bias  = prefs.getFloat(KEY_GLOBAL_BIAS,  0.0f);
        Co2Estimator estimator = EcoLumsApp.getCo2Estimator();
        if (estimator != null) {
            estimator.setGlobalCorrection(scale, bias);
        }
    }

    // -------------------------------------------------------------------------
    // SharedPreferences helpers
    // -------------------------------------------------------------------------

    private List<float[]> loadExamples() {
        String json = prefs.getString(KEY_EXAMPLES, "[]");
        List<float[]> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new float[]{
                        (float) o.getDouble("out"),
                        (float) o.getDouble("lbl")
                });
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void saveExamples(List<float[]> examples) {
        try {
            JSONArray arr = new JSONArray();
            for (float[] e : examples) {
                JSONObject o = new JSONObject();
                o.put("out", e[0]);
                o.put("lbl", e[1]);
                arr.put(o);
            }
            prefs.edit().putString(KEY_EXAMPLES, arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}
