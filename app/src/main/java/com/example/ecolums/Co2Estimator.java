package com.example.ecolums;

import android.content.Context;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;

/**
 * On-device CO₂ estimator backed by a TFLite neural network.
 *
 * <p>The model ({@code ecolums_co2_model.tflite} in {@code assets/}) was trained in
 * {@code ml/co2_model_training.ipynb}. It accepts a 6-feature vector and returns
 * a single float — the estimated CO₂ equivalent in kilograms (positive = saved,
 * negative = emitted).</p>
 *
 * <p><b>Feature vector order</b> (must match the notebook):</p>
 * <ol>
 *   <li>{@code sub_type}    — one of the {@code SUBTYPE_*} constants</li>
 *   <li>{@code quantity}    — km (transport), kWh (energy), kg (waste)</li>
 *   <li>{@code hour_of_day} — 0–23</li>
 *   <li>{@code day_of_week} — 0=Monday … 6=Sunday</li>
 *   <li>{@code month}       — 1–12</li>
 *   <li>{@code is_weekend}  — 0.0 or 1.0</li>
 * </ol>
 *
 * <p>Initialise once via {@link EcoLumsApp} and access through
 * {@link EcoLumsApp#getCo2Estimator()}.</p>
 */
public class Co2Estimator {

    // -------------------------------------------------------------------------
    // Sub-type encodings — must stay in sync with ml/co2_model_training.ipynb
    // -------------------------------------------------------------------------

    public static final int SUBTYPE_BUS      = 0;
    public static final int SUBTYPE_BIKE     = 1;
    public static final int SUBTYPE_CAR      = 2;
    public static final int SUBTYPE_WALK     = 3;
    public static final int SUBTYPE_TRAIN    = 4;
    public static final int SUBTYPE_ENERGY   = 5;
    public static final int SUBTYPE_LANDFILL = 6;
    public static final int SUBTYPE_RECYCLE  = 7;
    public static final int SUBTYPE_COMPOST  = 8;

    private static final String MODEL_ASSET = "ecolums_co2_model.tflite";

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private Interpreter interpreter;

    /** FL global correction — applied on top of the base model output. */
    private volatile float globalScale = 1.0f;
    private volatile float globalBias  = 0.0f;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Loads the TFLite model from assets. If the model file is absent or
     * corrupt, {@link #isAvailable()} returns {@code false} and all
     * {@link #estimate} calls return {@link Float#NaN}.
     *
     * @param context Any {@link Context} — application context preferred.
     */
    public Co2Estimator(Context context) {
        try {
            interpreter = new Interpreter(loadModelFile(context));
        } catch (Throwable e) {
            Log.e("Co2Estimator", "Failed to load TFLite model: " + e.getClass().getName() + " — " + e.getMessage(), e);
            interpreter = null;
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

    // -------------------------------------------------------------------------
    // Inference
    // -------------------------------------------------------------------------

    /**
     * Estimates the CO₂ equivalent (kg) for an activity.
     *
     * @param subType  One of the {@code SUBTYPE_*} constants.
     * @param quantity Distance in km (transport), kWh (energy), or kg (waste).
     * @param calendar Timestamp of the activity, used to extract hour/day/month.
     * @return Estimated CO₂ kg, or {@link Float#NaN} if the model is unavailable.
     */
    public float estimate(int subType, float quantity, Calendar calendar) {
        if (interpreter == null) return Float.NaN;

        int   hour      = calendar.get(Calendar.HOUR_OF_DAY);
        // Calendar.DAY_OF_WEEK: 1=Sun … 7=Sat → remap to 0=Mon … 6=Sun
        int   rawDow    = calendar.get(Calendar.DAY_OF_WEEK);
        int   dow       = (rawDow == Calendar.SUNDAY) ? 6 : rawDow - 2;
        int   month     = calendar.get(Calendar.MONTH) + 1; // Calendar.MONTH is 0-based
        float isWeekend = (rawDow == Calendar.SATURDAY || rawDow == Calendar.SUNDAY) ? 1.0f : 0.0f;

        float[][] input  = {{ subType, quantity, hour, dow, month, isWeekend }};
        float[][] output = {{ 0.0f }};

        try {
            interpreter.run(input, output);
        } catch (Throwable e) {
            return Float.NaN;
        }

        // Apply federated learning global correction (identity by default: scale=1, bias=0)
        return globalScale * output[0][0] + globalBias;
    }

    /**
     * Updates the federated learning correction applied on top of the base model.
     * Called by {@link FederatedLearningManager} after downloading the latest FedAvg result.
     *
     * @param scale Multiplicative correction (1.0 = no change).
     * @param bias  Additive correction (0.0 = no change).
     */
    public void setGlobalCorrection(float scale, float bias) {
        this.globalScale = scale;
        this.globalBias  = bias;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** @return {@code true} if the model loaded successfully and is ready to use. */
    public boolean isAvailable() {
        return interpreter != null;
    }

    /** Releases the TFLite interpreter. Call when the app is shutting down. */
    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }
}
