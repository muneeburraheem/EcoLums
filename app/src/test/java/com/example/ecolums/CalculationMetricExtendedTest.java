package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * Extended unit tests for CalculationMetric — covers all setter/getter pairs,
 * the lastUpdated audit fields, and invariants not covered in the primary
 * CalculationMetricTest suite.
 * <p>
 * CalculationMetric is a pure-Java model with no Firebase dependencies.
 */
public class CalculationMetricExtendedTest {

    // -------------------------------------------------------------------------
    // No-arg constructor
    // -------------------------------------------------------------------------

    /**
     * AC: No-arg constructor creates an instance with all fields null / 0.
     */
    @Test
    public void noArgConstructor_allFieldsAreNullOrZero() {
        CalculationMetric m = new CalculationMetric();
        assertNull(m.getMetricId());
        assertNull(m.getMetricKey());
        assertNull(m.getDisplayName());
        assertEquals(0.0, m.getValue(), 1e-9);
        assertNull(m.getUnit());
        assertNull(m.getReferenceStandard());
        assertNull(m.getLastUpdated());
        assertNull(m.getLastUpdatedByAdminId());
    }

    // -------------------------------------------------------------------------
    // Setter/getter round-trips for all fields
    // -------------------------------------------------------------------------

    @Test
    public void metricId_setAndGet() {
        CalculationMetric m = new CalculationMetric();
        m.setMetricId("new-id");
        assertEquals("new-id", m.getMetricId());
    }

    @Test
    public void metricKey_setAndGet() {
        CalculationMetric m = new CalculationMetric();
        m.setMetricKey(CalculationMetric.KEY_ENERGY_PER_KWH);
        assertEquals(CalculationMetric.KEY_ENERGY_PER_KWH, m.getMetricKey());
    }

    @Test
    public void displayName_setAndGet() {
        CalculationMetric m = new CalculationMetric();
        m.setDisplayName("Energy saved per kWh");
        assertEquals("Energy saved per kWh", m.getDisplayName());
    }

    @Test
    public void value_setAndGet() {
        CalculationMetric m = new CalculationMetric();
        m.setValue(0.42);
        assertEquals(0.42, m.getValue(), 1e-9);
    }

    @Test
    public void unit_setAndGet() {
        CalculationMetric m = new CalculationMetric();
        m.setUnit("kg CO2 saved / kWh");
        assertEquals("kg CO2 saved / kWh", m.getUnit());
    }

    @Test
    public void referenceStandard_setAndGet() {
        CalculationMetric m = new CalculationMetric();
        m.setReferenceStandard("IEA 2023");
        assertEquals("IEA 2023", m.getReferenceStandard());
    }

    /**
     * AC: lastUpdated can be set to a specific timestamp.
     */
    @Test
    public void lastUpdated_setAndGet() {
        CalculationMetric m = new CalculationMetric();
        Timestamp ts = new Timestamp(1_700_000_000L, 0);
        m.setLastUpdated(ts);
        assertEquals(ts, m.getLastUpdated());
    }

    /**
     * AC: lastUpdatedByAdminId can be set and retrieved.
     */
    @Test
    public void lastUpdatedByAdminId_setAndGet() {
        CalculationMetric m = new CalculationMetric();
        m.setLastUpdatedByAdminId("admin-uid-xyz");
        assertEquals("admin-uid-xyz", m.getLastUpdatedByAdminId());
    }

    // -------------------------------------------------------------------------
    // Audit fields — default then set
    // -------------------------------------------------------------------------

    /**
     * AC: After updating lastUpdated, the field reflects the new timestamp.
     */
    @Test
    public void lastUpdated_afterEdit_isNonNull() {
        CalculationMetric m = new CalculationMetric(
                "m1", CalculationMetric.KEY_BIKE_PER_KM, "Bike", 0.21, "unit", "ref");
        assertNull(m.getLastUpdated()); // still null before admin update
        m.setLastUpdated(new Timestamp(1_700_000_000L, 0));
        assertNotNull(m.getLastUpdated());
    }

    /**
     * AC: After an admin update, lastUpdatedByAdminId is set.
     */
    @Test
    public void lastUpdatedByAdminId_afterEdit_isNonNull() {
        CalculationMetric m = new CalculationMetric(
                "m1", CalculationMetric.KEY_BUS_PER_KM, "Bus", 0.05, "unit", "ref");
        assertNull(m.getLastUpdatedByAdminId());
        m.setLastUpdatedByAdminId("admin-007");
        assertEquals("admin-007", m.getLastUpdatedByAdminId());
    }

    // -------------------------------------------------------------------------
    // Value edge cases
    // -------------------------------------------------------------------------

    /**
     * AC: A value of 0.0 is valid (e.g., for a free-of-CO2 activity).
     */
    @Test
    public void value_canBeZero() {
        CalculationMetric m = new CalculationMetric("id", "KEY", "name", 0.0, "unit", "ref");
        assertEquals(0.0, m.getValue(), 1e-9);
    }

    /**
     * AC: A negative value is valid (e.g., CAR_PER_KM emits CO2).
     */
    @Test
    public void value_canBeNegative() {
        CalculationMetric m = new CalculationMetric("id", "CAR_PER_KM", "Car", -0.21, "unit", "ref");
        assertEquals(-0.21, m.getValue(), 1e-9);
    }

    /**
     * AC: A large positive value is stored correctly.
     */
    @Test
    public void value_largePositive_storedCorrectly() {
        CalculationMetric m = new CalculationMetric("id", "KEY", "name", 999999.99, "unit", "ref");
        assertEquals(999999.99, m.getValue(), 1e-6);
    }

    // -------------------------------------------------------------------------
    // All metric keys are unique
    // -------------------------------------------------------------------------

    /**
     * AC: All KEY_* constants are distinct — no two metrics share the same key.
     */
    @Test
    public void allMetricKeyConstants_areUnique() {
        Set<String> keys = new HashSet<>();
        keys.add(CalculationMetric.KEY_BIKE_PER_KM);
        keys.add(CalculationMetric.KEY_BUS_PER_KM);
        keys.add(CalculationMetric.KEY_CAR_PER_KM);
        keys.add(CalculationMetric.KEY_TRAIN_PER_KM);
        keys.add(CalculationMetric.KEY_WALK_PER_KM);
        keys.add(CalculationMetric.KEY_ENERGY_PER_KWH);
        keys.add(CalculationMetric.KEY_WASTE_LANDFILL_KG);
        keys.add(CalculationMetric.KEY_WASTE_RECYCLE_KG);
        keys.add(CalculationMetric.KEY_WASTE_COMPOST_KG);
        assertEquals("Expected 9 unique metric keys", 9, keys.size());
    }

    /**
     * AC: The KEY_WASTE_LANDFILL_KG matches the wasteKey pattern used by ActivityLog
     * ("LANDFILL" + "_KG").
     */
    @Test
    public void wasteLandfillKey_matchesActivityLogLookupConvention() {
        String expected = ActivityLog.WASTE_LANDFILL + "_KG";
        assertEquals(expected, CalculationMetric.KEY_WASTE_LANDFILL_KG);
    }

    /**
     * AC: KEY_WASTE_RECYCLE_KG matches the wasteKey pattern.
     */
    @Test
    public void wasteRecycleKey_matchesActivityLogLookupConvention() {
        String expected = ActivityLog.WASTE_RECYCLE + "_KG";
        assertEquals(expected, CalculationMetric.KEY_WASTE_RECYCLE_KG);
    }

    /**
     * AC: KEY_WASTE_COMPOST_KG matches the wasteKey pattern.
     */
    @Test
    public void wasteCompostKey_matchesActivityLogLookupConvention() {
        String expected = ActivityLog.WASTE_COMPOST + "_KG";
        assertEquals(expected, CalculationMetric.KEY_WASTE_COMPOST_KG);
    }

    /**
     * AC: KEY_BUS_PER_KM matches the transport key pattern.
     */
    @Test
    public void busKey_matchesActivityLogLookupConvention() {
        String expected = ActivityLog.TRANSPORT_BUS + "_PER_KM";
        assertEquals(expected, CalculationMetric.KEY_BUS_PER_KM);
    }

    /**
     * AC: KEY_CAR_PER_KM matches the transport key pattern.
     */
    @Test
    public void carKey_matchesActivityLogLookupConvention() {
        String expected = ActivityLog.TRANSPORT_CAR + "_PER_KM";
        assertEquals(expected, CalculationMetric.KEY_CAR_PER_KM);
    }

    /**
     * AC: KEY_TRAIN_PER_KM matches the transport key pattern.
     */
    @Test
    public void trainKey_matchesActivityLogLookupConvention() {
        String expected = ActivityLog.TRANSPORT_TRAIN + "_PER_KM";
        assertEquals(expected, CalculationMetric.KEY_TRAIN_PER_KM);
    }

    /**
     * AC: KEY_WALK_PER_KM matches the transport key pattern.
     */
    @Test
    public void walkKey_matchesActivityLogLookupConvention() {
        String expected = ActivityLog.TRANSPORT_WALK + "_PER_KM";
        assertEquals(expected, CalculationMetric.KEY_WALK_PER_KM);
    }
}
