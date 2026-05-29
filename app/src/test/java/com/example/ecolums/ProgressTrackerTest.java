package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Unit tests for ProgressTracker — covers US_05.02 (Personal Progress Tracking)
 * and US_06.01 (Milestone / Badge checking logic).
 * <p>
 * Firestore-backed async methods (getPersonalImpactSummary, checkMilestones etc.)
 * are not tested here since they require a live or emulated Firestore instance.
 * These tests focus on the pure-Java inner data classes and any synchronous logic.
 */
public class ProgressTrackerTest {

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
	// ImpactSummary inner class
	// -------------------------------------------------------------------------

	/**
	 * AC: Default ImpactSummary has all fields set to 0.0.
	 */
	@Test
	public void impactSummary_defaultValues_allZero() {
		ProgressTracker.ImpactSummary summary = new ProgressTracker.ImpactSummary();
		assertEquals(0.0, summary.totalCO2SavedKg, 1e-9);
		assertEquals(0.0, summary.totalWasteDivertedKg, 1e-9);
		assertEquals(0.0, summary.totalEnergySavedKwh, 1e-9);
		assertEquals(0.0, summary.totalPointsEarned, 1e-9);
	}

	/**
	 * AC: ImpactSummary fields can be accumulated (simulates adding up log entries).
	 */
	@Test
	public void impactSummary_accumulate_addsCO2Correctly() {
		ProgressTracker.ImpactSummary summary = new ProgressTracker.ImpactSummary();
		summary.totalCO2SavedKg += 2.5;
		summary.totalCO2SavedKg += 1.5;
		assertEquals(4.0, summary.totalCO2SavedKg, 1e-9);
	}

	/**
	 * AC: Waste and energy fields accumulate independently.
	 */
	@Test
	public void impactSummary_wasteAndEnergy_accumulateIndependently() {
		ProgressTracker.ImpactSummary summary = new ProgressTracker.ImpactSummary();
		summary.totalWasteDivertedKg += 3.0;
		summary.totalEnergySavedKwh += 10.0;
		assertEquals(3.0, summary.totalWasteDivertedKg, 1e-9);
		assertEquals(10.0, summary.totalEnergySavedKwh, 1e-9);
	}

	/**
	 * AC: Points accumulate alongside CO2.
	 */
	@Test
	public void impactSummary_pointsAccumulate() {
		ProgressTracker.ImpactSummary summary = new ProgressTracker.ImpactSummary();
		summary.totalPointsEarned += 25.0;
		summary.totalPointsEarned += 50.0;
		assertEquals(75.0, summary.totalPointsEarned, 1e-9);
	}

	// -------------------------------------------------------------------------
	// DataPoint inner class
	// -------------------------------------------------------------------------

	/**
	 * AC: DataPoint constructor sets label and value.
	 */
	@Test
	public void dataPoint_constructor_setsLabelAndValue() {
		ProgressTracker.DataPoint dp = new ProgressTracker.DataPoint("Mon", 12.5);
		assertEquals("Mon", dp.label);
		assertEquals(12.5, dp.value, 1e-9);
	}

	/**
	 * AC: DataPoint fields are mutable (used when building chart series).
	 */
	@Test
	public void dataPoint_fieldsAreMutable() {
		ProgressTracker.DataPoint dp = new ProgressTracker.DataPoint("Mon", 0.0);
		dp.label = "Tue";
		dp.value = 7.3;
		assertEquals("Tue", dp.label);
		assertEquals(7.3, dp.value, 1e-9);
	}

	// -------------------------------------------------------------------------
	// US_06.01 — Milestone threshold logic (pure-Java portion)
	// -------------------------------------------------------------------------

	/**
	 * AC: A badge with pointThreshold > 0 should be considered for award
	 * only when the user's totalPoints meets or exceeds the threshold.
	 * This replicates the condition in checkMilestonesForUser().
	 */
	@Test
	public void milestoneCheck_thresholdMet_badgeQualifies() {
		Badge badge = new Badge("b1", "Green Star", "desc", "criteria", "url", 100);
		double userPoints = 150.0;
		assertTrue(userPoints >= badge.getPointThreshold() && badge.getPointThreshold() > 0);
	}

	/**
	 * AC: Points below the threshold do not qualify.
	 */
	@Test
	public void milestoneCheck_thresholdNotMet_badgeDoesNotQualify() {
		Badge badge = new Badge("b1", "Green Star", "desc", "criteria", "url", 500);
		double userPoints = 150.0;
		assertFalse(userPoints >= badge.getPointThreshold());
	}

	/**
	 * AC: A badge with threshold = 0 is action-triggered and is excluded from
	 * automatic point-based checking (condition: threshold > 0).
	 */
	@Test
	public void milestoneCheck_zeroThreshold_notAwardedAutomatically() {
		Badge badge = new Badge("b1", "Action Badge", "desc", "criteria", "url", 0);
		double userPoints = 9999.0;
		// Condition from checkMilestonesForUser: threshold > 0
		assertFalse(badge.getPointThreshold() > 0);
	}

	// -------------------------------------------------------------------------
	// ProgressTracker construction (sanity check)
	// -------------------------------------------------------------------------

	/**
	 * AC: ProgressTracker can be instantiated (Firebase is mocked).
	 */
	@Test
	public void progressTracker_canBeInstantiated() {
		ProgressTracker tracker = new ProgressTracker();
		assertNotNull(tracker);
	}
}
