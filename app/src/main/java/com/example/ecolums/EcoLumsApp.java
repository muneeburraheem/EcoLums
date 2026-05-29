package com.example.ecolums;

import android.app.Application;

/**
 * Application subclass — initialises the on-device CO₂ estimator once at startup
 * so every call to {@link ActivityLog#convertToCO2WithML} can reuse the same
 * {@link Interpreter} instance without re-loading the TFLite model from assets.
 *
 * <p>Registered in {@code AndroidManifest.xml} via {@code android:name=".EcoLumsApp"}.</p>
 */
public class EcoLumsApp extends Application {

    private static Co2Estimator            co2Estimator;
    private static AnomalyDetector         anomalyDetector;
    private static FederatedLearningManager flManager;

    @Override
    public void onCreate() {
        super.onCreate();
        co2Estimator    = new Co2Estimator(this);
        anomalyDetector = new AnomalyDetector(this);
        flManager       = new FederatedLearningManager(this);

        // Restore last-known FL correction immediately (before async Firestore fetch)
        flManager.restoreCachedCorrection();
        // Then fetch the latest correction in the background
        flManager.syncGlobalCorrection();
    }

    /** Returns the shared {@link Co2Estimator}, or {@code null} before {@link #onCreate}. */
    public static Co2Estimator getCo2Estimator() {
        return co2Estimator;
    }

    /** Returns the shared {@link AnomalyDetector}, or {@code null} before {@link #onCreate}. */
    public static AnomalyDetector getAnomalyDetector() {
        return anomalyDetector;
    }

    /** Returns the shared {@link FederatedLearningManager}. */
    public static FederatedLearningManager getFlManager() {
        return flManager;
    }
}
