package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Unit tests for CalculationMetric — covers the admin-managed CO2 conversion
 * factors used in US_02.01 and the admin metric-editing feature (US_08.x).
 * <p>
 * CalculationMetric is a pure-Java model with no Firebase dependencies.
 */
public class CalculationMetricTest {

	// -------------------------------------------------------------------------
	// Key constants — must match Firestore document IDs and ActivityLog lookups
	// -------------------------------------------------------------------------

	@Test
	public void keyConstant_bikePerkm_hasCorrectValue() {
		assertEquals("BIKE_PER_KM", CalculationMetric.KEY_BIKE_PER_KM);
	}

	@Test
	public void keyConstant_busPerkm_hasCorrectValue() {
		assertEquals("BUS_PER_KM", CalculationMetric.KEY_BUS_PER_KM);
	}

	@Test
	public void keyConstant_carPerkm_hasCorrectValue() {
		assertEquals("CAR_PER_KM", CalculationMetric.KEY_CAR_PER_KM);
	}

	@Test
	public void keyConstant_trainPerkm_hasCorrectValue() {
		assertEquals("TRAIN_PER_KM", CalculationMetric.KEY_TRAIN_PER_KM);
	}

	@Test
	public void keyConstant_walkPerkm_hasCorrectValue() {
		assertEquals("WALK_PER_KM", CalculationMetric.KEY_WALK_PER_KM);
	}

	@Test
	public void keyConstant_energyPerKwh_hasCorrectValue() {
		assertEquals("ENERGY_PER_KWH", CalculationMetric.KEY_ENERGY_PER_KWH);
	}

	@Test
	public void keyConstant_wasteLandfillKg_hasCorrectValue() {
		assertEquals("LANDFILL_KG", CalculationMetric.KEY_WASTE_LANDFILL_KG);
	}

	@Test
	public void keyConstant_wasteRecycleKg_hasCorrectValue() {
		assertEquals("RECYCLE_KG", CalculationMetric.KEY_WASTE_RECYCLE_KG);
	}

	@Test
	public void keyConstant_wasteCompostKg_hasCorrectValue() {
		assertEquals("COMPOST_KG", CalculationMetric.KEY_WASTE_COMPOST_KG);
	}

	// -------------------------------------------------------------------------
	// Constructor
	// -------------------------------------------------------------------------

	/**
	 * AC: Constructor persists all provided fields.
	 */
	@Test
	public void constructor_setsAllProvidedFields() {
		CalculationMetric m = new CalculationMetric(
				"metric-1",
				CalculationMetric.KEY_BIKE_PER_KM,
				"Bike CO2 per km",
				0.21,
				"kg CO2 saved / km",
				"EPA 2024"
		);

		assertEquals("metric-1", m.getMetricId());
		assertEquals(CalculationMetric.KEY_BIKE_PER_KM, m.getMetricKey());
		assertEquals("Bike CO2 per km", m.getDisplayName());
		assertEquals(0.21, m.getValue(), 1e-9);
		assertEquals("kg CO2 saved / km", m.getUnit());
		assertEquals("EPA 2024", m.getReferenceStandard());
	}

	/**
	 * AC: lastUpdated is null until explicitly set.
	 */
	@Test
	public void lastUpdated_isNullByDefault() {
		CalculationMetric m = new CalculationMetric(
				"m1", "KEY", "display", 1.0, "unit", "ref");
		assertNull(m.getLastUpdated());
	}

	/**
	 * AC: lastUpdatedByAdminId is null until explicitly set.
	 */
	@Test
	public void lastUpdatedByAdminId_isNullByDefault() {
		CalculationMetric m = new CalculationMetric(
				"m1", "KEY", "display", 1.0, "unit", "ref");
		assertNull(m.getLastUpdatedByAdminId());
	}

	/**
	 * AC: value can be updated via setter (admin edits the metric).
	 */
	@Test
	public void setValue_updatesConversionFactor() {
		CalculationMetric m = new CalculationMetric(
				"m1", CalculationMetric.KEY_ENERGY_PER_KWH, "Energy", 0.50, "unit", "ref");
		m.setValue(0.45);
		assertEquals(0.45, m.getValue(), 1e-9);
	}

	/**
	 * AC: ActivityLog builds the metric map key as transportMode + "_PER_KM".
	 * This test verifies that the constant aligns with that convention.
	 */
	@Test
	public void bikeKey_matchesActivityLogLookupConvention() {
		String expected = ActivityLog.TRANSPORT_BIKE + "_PER_KM";
		assertEquals(expected, CalculationMetric.KEY_BIKE_PER_KM);
	}
}
