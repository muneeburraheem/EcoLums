package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Extended unit tests for ActivityLog — covers guard conditions in {@code editLog}
 * and {@code deleteLog}, additional CO2/points edge cases, getters/setters, and
 * boundary values not covered by the primary ActivityLogTest suite.
 * <p>
 * Firebase is mocked so constructors do not throw; all synchronous guard logic
 * is tested without a live Firestore instance.
 */
public class ActivityLogExtendedTest {

    private MockedStatic<FirebaseFirestore> mockedFirestore;

    @Before
    public void setUp() {
        FirebaseFirestore mockDb = mock(FirebaseFirestore.class);
        mockedFirestore = Mockito.mockStatic(FirebaseFirestore.class);
        mockedFirestore.when(FirebaseFirestore::getInstance).thenReturn(mockDb);
    }

    @After
    public void tearDown() {
        mockedFirestore.close();
    }

    // -------------------------------------------------------------------------
    // editLog — null guards (synchronous early-exit)
    // -------------------------------------------------------------------------

    /**
     * AC: editLog returns false immediately when logId is null.
     */
    @Test
    public void editLog_nullLogId_callsCallbackWithFalse() {
        ActivityLog log = new ActivityLog("user1", null, ActivityLog.CATEGORY_TRANSPORT);
        // logId is null (not yet assigned after submitLog)
        AtomicBoolean result = new AtomicBoolean(true);
        log.editLog(success -> result.set(success));
        assertFalse(result.get());
    }

    /**
     * AC: editLog returns false when userId is null (even if logId is set).
     */
    @Test
    public void editLog_nullUserId_callsCallbackWithFalse() {
        ActivityLog log = new ActivityLog();
        log.setLogId("log-123");
        // userId is null
        AtomicBoolean result = new AtomicBoolean(true);
        log.editLog(success -> result.set(success));
        assertFalse(result.get());
    }

    /**
     * AC: editLog returns false when both logId and userId are null.
     */
    @Test
    public void editLog_nullLogIdAndUserId_callsCallbackWithFalse() {
        ActivityLog log = new ActivityLog();
        AtomicBoolean result = new AtomicBoolean(true);
        log.editLog(success -> result.set(success));
        assertFalse(result.get());
    }

    // -------------------------------------------------------------------------
    // deleteLog — null guards (synchronous early-exit)
    // -------------------------------------------------------------------------

    /**
     * AC: deleteLog returns false immediately when logId is null.
     */
    @Test
    public void deleteLog_nullLogId_callsCallbackWithFalse() {
        ActivityLog log = new ActivityLog("user1", null, ActivityLog.CATEGORY_TRANSPORT);
        AtomicBoolean result = new AtomicBoolean(true);
        log.deleteLog(success -> result.set(success));
        assertFalse(result.get());
    }

    /**
     * AC: deleteLog returns false when userId is null (even if logId is set).
     */
    @Test
    public void deleteLog_nullUserId_callsCallbackWithFalse() {
        ActivityLog log = new ActivityLog();
        log.setLogId("log-456");
        AtomicBoolean result = new AtomicBoolean(true);
        log.deleteLog(success -> result.set(success));
        assertFalse(result.get());
    }

    /**
     * AC: deleteLog returns false when both logId and userId are null.
     */
    @Test
    public void deleteLog_nullLogIdAndUserId_callsCallbackWithFalse() {
        ActivityLog log = new ActivityLog();
        AtomicBoolean result = new AtomicBoolean(true);
        log.deleteLog(success -> result.set(success));
        assertFalse(result.get());
    }

    // -------------------------------------------------------------------------
    // calculatePoints — boundary values
    // -------------------------------------------------------------------------

    /**
     * AC: Very small positive CO2 (0.001 kg) still produces positive points.
     */
    @Test
    public void calculatePoints_verySmallPositiveCO2_producesPositivePoints() {
        ActivityLog log = new ActivityLog("u", null, ActivityLog.CATEGORY_TRANSPORT);
        log.setCo2EquivalentKg(0.001);
        log.calculatePoints();
        assertEquals(0.001 * 10.0, log.getPointsEarned(), 1e-9);
    }

    /**
     * AC: Very large positive CO2 scales correctly.
     */
    @Test
    public void calculatePoints_largeCO2_scalesCorrectly() {
        ActivityLog log = new ActivityLog("u", null, ActivityLog.CATEGORY_TRANSPORT);
        log.setCo2EquivalentKg(1000.0);
        log.calculatePoints();
        assertEquals(10000.0, log.getPointsEarned(), 1e-9);
    }

    /**
     * AC: Fractional CO2 produces the correct fractional points.
     */
    @Test
    public void calculatePoints_fractionalCO2_producesCorrectPoints() {
        ActivityLog log = new ActivityLog("u", null, ActivityLog.CATEGORY_ENERGY);
        log.setCo2EquivalentKg(2.5);
        log.calculatePoints();
        assertEquals(25.0, log.getPointsEarned(), 1e-9);
    }

    /**
     * AC: A very large negative CO2 still yields zero points (max clamp).
     */
    @Test
    public void calculatePoints_largeNegativeCO2_yieldsZeroPoints() {
        ActivityLog log = new ActivityLog("u", null, ActivityLog.CATEGORY_TRANSPORT);
        log.setCo2EquivalentKg(-500.0);
        log.calculatePoints();
        assertEquals(0.0, log.getPointsEarned(), 1e-9);
    }

    // -------------------------------------------------------------------------
    // convertToCO2 — edge cases
    // -------------------------------------------------------------------------

    /**
     * AC: Transport log with null transportMode gets factor 0 → CO2 = 0.
     */
    @Test
    public void convertToCO2_transportWithNullMode_yieldZeroCO2() {
        ActivityLog log = new ActivityLog("u", null, ActivityLog.CATEGORY_TRANSPORT);
        log.setTransportMode(null);
        log.setDistanceKm(10.0);
        log.convertToCO2(new HashMap<>());
        assertEquals(0.0, log.getCo2EquivalentKg(), 1e-9);
    }

    /**
     * AC: Energy log with 0 kWh yields 0 CO2 regardless of the factor.
     */
    @Test
    public void convertToCO2_energyZeroKwh_yieldsZeroCO2() {
        ActivityLog log = new ActivityLog("u", null, ActivityLog.CATEGORY_ENERGY);
        log.setEnergyKwh(0.0);
        log.convertToCO2(metricsOf(CalculationMetric.KEY_ENERGY_PER_KWH, 0.5));
        assertEquals(0.0, log.getCo2EquivalentKg(), 1e-9);
    }

    /**
     * AC: Waste log with 0 kg yields 0 CO2 regardless of waste type.
     */
    @Test
    public void convertToCO2_wasteZeroKg_yieldsZeroCO2() {
        ActivityLog log = new ActivityLog("u", null, ActivityLog.CATEGORY_WASTE);
        log.setWasteType(ActivityLog.WASTE_RECYCLE);
        log.setWasteKg(0.0);
        log.convertToCO2(metricsOf(CalculationMetric.KEY_WASTE_RECYCLE_KG, 0.1));
        assertEquals(0.0, log.getCo2EquivalentKg(), 1e-9);
    }

    /**
     * AC: A transport log with 0 km yields 0 CO2 even if a factor is present.
     */
    @Test
    public void convertToCO2_transportZeroDistance_yieldsZeroCO2() {
        ActivityLog log = new ActivityLog("u", null, ActivityLog.CATEGORY_TRANSPORT);
        log.setTransportMode(ActivityLog.TRANSPORT_BIKE);
        log.setDistanceKm(0.0);
        log.convertToCO2(metricsOf(CalculationMetric.KEY_BIKE_PER_KM, 0.21));
        assertEquals(0.0, log.getCo2EquivalentKg(), 1e-9);
    }

    /**
     * AC: convertToCO2 with unknown waste type (no matching key) yields 0 CO2.
     */
    @Test
    public void convertToCO2_unknownWasteType_missingKey_yieldsZeroCO2() {
        ActivityLog log = new ActivityLog("u", null, ActivityLog.CATEGORY_WASTE);
        log.setWasteType("UNKNOWN_WASTE");
        log.setWasteKg(5.0);
        log.convertToCO2(new HashMap<>());
        assertEquals(0.0, log.getCo2EquivalentKg(), 1e-9);
    }

    // -------------------------------------------------------------------------
    // Getters and setters — all fields
    // -------------------------------------------------------------------------

    /**
     * AC: All getter/setter pairs on ActivityLog work correctly.
     */
    @Test
    public void allGetterSetterPairs_roundTrip() {
        ActivityLog log = new ActivityLog();

        log.setLogId("log-999");
        assertEquals("log-999", log.getLogId());

        log.setUserId("user-abc");
        assertEquals("user-abc", log.getUserId());

        log.setCategory(ActivityLog.CATEGORY_WASTE);
        assertEquals(ActivityLog.CATEGORY_WASTE, log.getCategory());

        log.setTransportMode(ActivityLog.TRANSPORT_BUS);
        assertEquals(ActivityLog.TRANSPORT_BUS, log.getTransportMode());

        log.setDistanceKm(42.5);
        assertEquals(42.5, log.getDistanceKm(), 1e-9);

        log.setEnergyKwh(15.0);
        assertEquals(15.0, log.getEnergyKwh(), 1e-9);

        log.setWasteType(ActivityLog.WASTE_COMPOST);
        assertEquals(ActivityLog.WASTE_COMPOST, log.getWasteType());

        log.setWasteKg(3.7);
        assertEquals(3.7, log.getWasteKg(), 1e-9);

        log.setCo2EquivalentKg(2.1);
        assertEquals(2.1, log.getCo2EquivalentKg(), 1e-9);

        log.setPointsEarned(21.0);
        assertEquals(21.0, log.getPointsEarned(), 1e-9);
    }

    /**
     * AC: No-arg constructor leaves date null.
     */
    @Test
    public void noArgConstructor_dateIsNull() {
        ActivityLog log = new ActivityLog();
        assertNull(log.getDate());
    }

    /**
     * AC: No-arg constructor leaves logId null.
     */
    @Test
    public void noArgConstructor_logIdIsNull() {
        ActivityLog log = new ActivityLog();
        assertNull(log.getLogId());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Map<String, CalculationMetric> metricsOf(String key, double factor) {
        CalculationMetric m = new CalculationMetric("id", key, "display", factor, "unit", "ref");
        Map<String, CalculationMetric> map = new HashMap<>();
        map.put(key, m);
        return map;
    }
}
