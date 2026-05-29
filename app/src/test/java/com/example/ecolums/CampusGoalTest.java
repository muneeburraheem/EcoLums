package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/**
 * Unit tests for CampusGoal — covers US_05.01 (Campus-Wide Impact Dashboard).
 * <p>
 * Tests focus on the derived-helper methods (getProgressPercentage,
 * getRemainingValue) and the default field values set by the constructor.
 * No Firebase interaction is needed for these tests.
 */
public class CampusGoalTest {

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Creates a CampusGoal with the given target and progress (no dates needed).
	 */
	private CampusGoal goalWith(double target, double progress) {
		CampusGoal goal = new CampusGoal(
				"goal1", "Test Goal", CampusGoal.METRIC_CO2,
				target, null, null, Arrays.asList(25, 50, 75, 100)
		);
		goal.setCurrentProgress(progress);
		return goal;
	}

	// -------------------------------------------------------------------------
	// US_05.01 — Progress percentage
	// -------------------------------------------------------------------------

	/**
	 * AC: 0 progress out of 1000 → 0%.
	 */
	@Test
	public void getProgressPercentage_atZero_returnsZero() {
		assertEquals(0.0, goalWith(1000, 0).getProgressPercentage(), 1e-9);
	}

	/**
	 * AC: 500 progress out of 1000 → 50%.
	 */
	@Test
	public void getProgressPercentage_halfway_returnsFifty() {
		assertEquals(50.0, goalWith(1000, 500).getProgressPercentage(), 1e-9);
	}

	/**
	 * AC: 1000 progress out of 1000 → 100%.
	 */
	@Test
	public void getProgressPercentage_complete_returnsHundred() {
		assertEquals(100.0, goalWith(1000, 1000).getProgressPercentage(), 1e-9);
	}

	/**
	 * AC: Progress exceeding the target returns a value over 100.
	 */
	@Test
	public void getProgressPercentage_exceeded_returnsOver100() {
		assertTrue(goalWith(1000, 1500).getProgressPercentage() > 100.0);
	}

	/**
	 * AC: Target of zero returns 0% to avoid division-by-zero.
	 */
	@Test
	public void getProgressPercentage_zeroTarget_returnsZero() {
		assertEquals(0.0, goalWith(0, 0).getProgressPercentage(), 1e-9);
	}

	/**
	 * AC: Percentage formula is (currentProgress / targetValue) * 100.
	 */
	@Test
	public void getProgressPercentage_formulaIsCorrect() {
		double target = 800.0;
		double progress = 200.0;
		double expected = (progress / target) * 100.0;
		assertEquals(expected, goalWith(target, progress).getProgressPercentage(), 1e-9);
	}

	// -------------------------------------------------------------------------
	// US_05.01 — Remaining value
	// -------------------------------------------------------------------------

	/**
	 * AC: Partial progress → remaining = target − progress.
	 */
	@Test
	public void getRemainingValue_partialProgress_returnsRemainder() {
		assertEquals(700.0, goalWith(1000, 300).getRemainingValue(), 1e-9);
	}

	/**
	 * AC: Progress exactly meets target → remaining = 0.
	 */
	@Test
	public void getRemainingValue_progressMeetsTarget_returnsZero() {
		assertEquals(0.0, goalWith(500, 500).getRemainingValue(), 1e-9);
	}

	/**
	 * AC: Progress exceeds target → remaining is clamped to 0 (not negative).
	 */
	@Test
	public void getRemainingValue_exceeded_clampedToZero() {
		assertEquals(0.0, goalWith(500, 600).getRemainingValue(), 1e-9);
	}

	// -------------------------------------------------------------------------
	// US_05.01 — Default state from constructor
	// -------------------------------------------------------------------------

	/**
	 * AC: New goal starts with status ACTIVE.
	 */
	@Test
	public void constructor_defaultStatusIsActive() {
		CampusGoal goal = new CampusGoal(
				"g1", "Title", CampusGoal.METRIC_CO2,
				1000, null, null, null
		);
		assertEquals(CampusGoal.STATUS_ACTIVE, goal.getStatus());
	}

	/**
	 * AC: New goal starts with currentProgress = 0.
	 */
	@Test
	public void constructor_initialProgressIsZero() {
		CampusGoal goal = new CampusGoal(
				"g1", "Title", CampusGoal.METRIC_CO2,
				1000, null, null, null
		);
		assertEquals(0.0, goal.getCurrentProgress(), 1e-9);
	}

	// -------------------------------------------------------------------------
	// Constants
	// -------------------------------------------------------------------------

	/**
	 * AC: Status constants match Firestore-stored string values.
	 */
	@Test
	public void statusConstants_haveCorrectValues() {
		assertEquals("ACTIVE", CampusGoal.STATUS_ACTIVE);
		assertEquals("COMPLETED", CampusGoal.STATUS_COMPLETED);
		assertEquals("ARCHIVED", CampusGoal.STATUS_ARCHIVED);
	}

	/**
	 * AC: Metric type constants match Firestore-stored string values.
	 */
	@Test
	public void metricTypeConstants_haveCorrectValues() {
		assertEquals("CO2", CampusGoal.METRIC_CO2);
		assertEquals("WASTE", CampusGoal.METRIC_WASTE);
		assertEquals("ENERGY", CampusGoal.METRIC_ENERGY);
	}
}
