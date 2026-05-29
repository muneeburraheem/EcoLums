package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for ActivityLog — covers US_02.01 (Eco Activity Logging)
 * and US_02.02 (CO2 calculation & points formula).
 * <p>
 * All tests are local JVM tests; Firebase is mocked so constructors
 * don't throw an IllegalStateException.
 */
public class ActivityLogTest {

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
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Builds a single-entry metrics map for a given key and factor.
	 */
	private Map<String, CalculationMetric> metricsOf(String key, double factor) {
		CalculationMetric m = new CalculationMetric("id", key, "display", factor, "unit", "ref");
		Map<String, CalculationMetric> map = new HashMap<>();
		map.put(key, m);
		return map;
	}

	/**
	 * Creates an ActivityLog with category and transport fields already set.
	 */
	private ActivityLog transportLog(String mode, double km) {
		ActivityLog log = new ActivityLog("user1", null, ActivityLog.CATEGORY_TRANSPORT);
		log.setTransportMode(mode);
		log.setDistanceKm(km);
		return log;
	}

	// -------------------------------------------------------------------------
	// US_02.01 — Transport CO2 calculation
	// -------------------------------------------------------------------------

	/**
	 * AC: Bike transport saves CO2; factor is positive.
	 */
	@Test
	public void convertToCO2_bikeTransport_calculatesCorrectly() {
		ActivityLog log = transportLog(ActivityLog.TRANSPORT_BIKE, 10.0);
		double factor = 0.21;
		log.convertToCO2(metricsOf(CalculationMetric.KEY_BIKE_PER_KM, factor));
		assertEquals(10.0 * factor, log.getCo2EquivalentKg(), 1e-9);
	}

	/**
	 * AC: Bus transport saves CO2; factor is positive.
	 */
	@Test
	public void convertToCO2_busTransport_calculatesCorrectly() {
		ActivityLog log = transportLog(ActivityLog.TRANSPORT_BUS, 20.0);
		double factor = 0.05;
		log.convertToCO2(metricsOf(CalculationMetric.KEY_BUS_PER_KM, factor));
		assertEquals(20.0 * factor, log.getCo2EquivalentKg(), 1e-9);
	}

	/**
	 * AC: Car transport emits CO2; factor is negative → negative CO2 equivalent.
	 */
	@Test
	public void convertToCO2_carTransport_yieldsNegativeCO2() {
		ActivityLog log = transportLog(ActivityLog.TRANSPORT_CAR, 15.0);
		double factor = -0.21;
		log.convertToCO2(metricsOf(CalculationMetric.KEY_CAR_PER_KM, factor));
		assertTrue(
				"Car travel should yield negative (emitted) CO2",
				log.getCo2EquivalentKg() < 0
		);
		assertEquals(15.0 * factor, log.getCo2EquivalentKg(), 1e-9);
	}

	/**
	 * AC: Walking saves CO2.
	 */
	@Test
	public void convertToCO2_walkTransport_calculatesCorrectly() {
		ActivityLog log = transportLog(ActivityLog.TRANSPORT_WALK, 5.0);
		double factor = 0.10;
		log.convertToCO2(metricsOf(CalculationMetric.KEY_WALK_PER_KM, factor));
		assertEquals(5.0 * factor, log.getCo2EquivalentKg(), 1e-9);
	}

	/**
	 * AC: Train transport saves CO2.
	 */
	@Test
	public void convertToCO2_trainTransport_calculatesCorrectly() {
		ActivityLog log = transportLog(ActivityLog.TRANSPORT_TRAIN, 30.0);
		double factor = 0.03;
		log.convertToCO2(metricsOf(CalculationMetric.KEY_TRAIN_PER_KM, factor));
		assertEquals(30.0 * factor, log.getCo2EquivalentKg(), 1e-9);
	}

	// -------------------------------------------------------------------------
	// US_02.01 — Energy CO2 calculation
	// -------------------------------------------------------------------------

	/**
	 * AC: Energy saving converts kWh to CO2 via the energy metric factor.
	 */
	@Test
	public void convertToCO2_energySaving_calculatesCorrectly() {
		ActivityLog log = new ActivityLog("user1", null, ActivityLog.CATEGORY_ENERGY);
		log.setEnergyKwh(8.0);
		double factor = 0.50;
		log.convertToCO2(metricsOf(CalculationMetric.KEY_ENERGY_PER_KWH, factor));
		assertEquals(8.0 * factor, log.getCo2EquivalentKg(), 1e-9);
	}

	// -------------------------------------------------------------------------
	// US_02.01 — Waste CO2 calculation
	// -------------------------------------------------------------------------

	/**
	 * AC: Landfill waste emits CO2; factor is negative.
	 */
	@Test
	public void convertToCO2_wasteLandfill_calculatesCorrectly() {
		ActivityLog log = new ActivityLog("user1", null, ActivityLog.CATEGORY_WASTE);
		log.setWasteType(ActivityLog.WASTE_LANDFILL);
		log.setWasteKg(2.0);
		double factor = -0.5;
		log.convertToCO2(metricsOf(CalculationMetric.KEY_WASTE_LANDFILL_KG, factor));
		assertEquals(2.0 * factor, log.getCo2EquivalentKg(), 1e-9);
	}

	/**
	 * AC: Recycling diverts waste from landfill; positive CO2 impact.
	 */
	@Test
	public void convertToCO2_wasteRecycle_calculatesCorrectly() {
		ActivityLog log = new ActivityLog("user1", null, ActivityLog.CATEGORY_WASTE);
		log.setWasteType(ActivityLog.WASTE_RECYCLE);
		log.setWasteKg(3.0);
		double factor = 0.10;
		log.convertToCO2(metricsOf(CalculationMetric.KEY_WASTE_RECYCLE_KG, factor));
		assertEquals(3.0 * factor, log.getCo2EquivalentKg(), 1e-9);
	}

	/**
	 * AC: Composting has a positive CO2 impact.
	 */
	@Test
	public void convertToCO2_wasteCompost_calculatesCorrectly() {
		ActivityLog log = new ActivityLog("user1", null, ActivityLog.CATEGORY_WASTE);
		log.setWasteType(ActivityLog.WASTE_COMPOST);
		log.setWasteKg(1.5);
		double factor = 0.20;
		log.convertToCO2(metricsOf(CalculationMetric.KEY_WASTE_COMPOST_KG, factor));
		assertEquals(1.5 * factor, log.getCo2EquivalentKg(), 1e-9);
	}

	/**
	 * AC: Unrecognised category results in zero CO2.
	 */
	@Test
	public void convertToCO2_unknownCategory_returnsZero() {
		ActivityLog log = new ActivityLog("user1", null, "UNKNOWN_CATEGORY");
		log.convertToCO2(new HashMap<>());
		assertEquals(0.0, log.getCo2EquivalentKg(), 1e-9);
	}

	/**
	 * AC: Missing metric key defaults factor to zero → zero CO2.
	 */
	@Test
	public void convertToCO2_missingMetricKey_returnsZero() {
		ActivityLog log = transportLog(ActivityLog.TRANSPORT_BIKE, 10.0);
		log.convertToCO2(new HashMap<>()); // no BIKE_PER_KM entry
		assertEquals(0.0, log.getCo2EquivalentKg(), 1e-9);
	}

	// -------------------------------------------------------------------------
	// US_02.02 — Points formula
	// -------------------------------------------------------------------------

	/**
	 * AC: 1 kg CO2 saved → 10 points.
	 */
	@Test
	public void calculatePoints_positiveCO2_earnsTenTimesPoints() {
		ActivityLog log = new ActivityLog("user1", null, ActivityLog.CATEGORY_TRANSPORT);
		log.setCo2EquivalentKg(5.0);
		log.calculatePoints();
		assertEquals(50.0, log.getPointsEarned(), 1e-9);
	}

	/**
	 * AC: Negative CO2 (e.g., car travel) earns zero points.
	 */
	@Test
	public void calculatePoints_negativeCO2_earnsZeroPoints() {
		ActivityLog log = new ActivityLog("user1", null, ActivityLog.CATEGORY_TRANSPORT);
		log.setCo2EquivalentKg(-3.0);
		log.calculatePoints();
		assertEquals(0.0, log.getPointsEarned(), 1e-9);
	}

	/**
	 * AC: Zero CO2 earns zero points.
	 */
	@Test
	public void calculatePoints_zeroCO2_earnsZeroPoints() {
		ActivityLog log = new ActivityLog("user1", null, ActivityLog.CATEGORY_ENERGY);
		log.setCo2EquivalentKg(0.0);
		log.calculatePoints();
		assertEquals(0.0, log.getPointsEarned(), 1e-9);
	}

	/**
	 * AC: convertToCO2 automatically calls calculatePoints and sets pointsEarned.
	 */
	@Test
	public void convertToCO2_alsoSetsPointsEarned() {
		ActivityLog log = transportLog(ActivityLog.TRANSPORT_BIKE, 10.0);
		double factor = 0.21;
		log.convertToCO2(metricsOf(CalculationMetric.KEY_BIKE_PER_KM, factor));
		double expectedCO2 = 10.0 * factor;
		double expectedPts = expectedCO2 * 10.0;
		assertEquals(expectedPts, log.getPointsEarned(), 1e-9);
	}

	/**
	 * AC: Category constants match values expected in Firestore queries.
	 */
	@Test
	public void categoryConstants_haveExpectedValues() {
		assertEquals("TRANSPORT", ActivityLog.CATEGORY_TRANSPORT);
		assertEquals("ENERGY", ActivityLog.CATEGORY_ENERGY);
		assertEquals("WASTE", ActivityLog.CATEGORY_WASTE);
	}

	/**
	 * AC: Transport mode constants have expected string values.
	 */
	@Test
	public void transportModeConstants_haveExpectedValues() {
		assertEquals("BUS", ActivityLog.TRANSPORT_BUS);
		assertEquals("BIKE", ActivityLog.TRANSPORT_BIKE);
		assertEquals("CAR", ActivityLog.TRANSPORT_CAR);
		assertEquals("WALK", ActivityLog.TRANSPORT_WALK);
		assertEquals("TRAIN", ActivityLog.TRANSPORT_TRAIN);
	}

	/**
	 * AC: Waste type constants have expected string values.
	 */
	@Test
	public void wasteTypeConstants_haveExpectedValues() {
		assertEquals("LANDFILL", ActivityLog.WASTE_LANDFILL);
		assertEquals("RECYCLE", ActivityLog.WASTE_RECYCLE);
		assertEquals("COMPOST", ActivityLog.WASTE_COMPOST);
	}
}
