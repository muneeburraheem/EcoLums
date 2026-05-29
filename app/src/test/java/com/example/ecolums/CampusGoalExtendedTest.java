package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Extended unit tests for CampusGoal — covers setters, dates, notification
 * milestones, all status/metric-type constants, and edge cases not addressed
 * in the primary CampusGoalTest suite.
 * <p>
 * CampusGoal is a pure-Java model with no Firebase dependencies
 * (Timestamp is constructed directly from epoch seconds).
 */
public class CampusGoalExtendedTest {

    // -------------------------------------------------------------------------
    // No-arg constructor
    // -------------------------------------------------------------------------

    /**
     * AC: No-arg constructor does not throw; all fields are null/0.
     */
    @Test
    public void noArgConstructor_allFieldsAreNullOrZero() {
        CampusGoal goal = new CampusGoal();
        assertNull(goal.getGoalId());
        assertNull(goal.getTitle());
        assertNull(goal.getMetricType());
        assertEquals(0.0, goal.getTargetValue(), 1e-9);
        assertEquals(0.0, goal.getCurrentProgress(), 1e-9);
        assertNull(goal.getStartDate());
        assertNull(goal.getEndDate());
        assertNull(goal.getStatus());
        assertNull(goal.getNotificationMilestones());
    }

    // -------------------------------------------------------------------------
    // Setter/getter round-trips
    // -------------------------------------------------------------------------

    @Test
    public void goalId_setAndGet() {
        CampusGoal goal = makeGoal();
        goal.setGoalId("new-goal-id");
        assertEquals("new-goal-id", goal.getGoalId());
    }

    @Test
    public void title_setAndGet() {
        CampusGoal goal = makeGoal();
        goal.setTitle("Reduce Campus Waste by 20%");
        assertEquals("Reduce Campus Waste by 20%", goal.getTitle());
    }

    @Test
    public void metricType_setAndGet() {
        CampusGoal goal = makeGoal();
        goal.setMetricType(CampusGoal.METRIC_ENERGY);
        assertEquals(CampusGoal.METRIC_ENERGY, goal.getMetricType());
    }

    @Test
    public void targetValue_setAndGet() {
        CampusGoal goal = makeGoal();
        goal.setTargetValue(5000.0);
        assertEquals(5000.0, goal.getTargetValue(), 1e-9);
    }

    @Test
    public void currentProgress_setAndGet() {
        CampusGoal goal = makeGoal();
        goal.setCurrentProgress(750.0);
        assertEquals(750.0, goal.getCurrentProgress(), 1e-9);
    }

    @Test
    public void status_setAndGet() {
        CampusGoal goal = makeGoal();
        goal.setStatus(CampusGoal.STATUS_COMPLETED);
        assertEquals(CampusGoal.STATUS_COMPLETED, goal.getStatus());
    }

    // -------------------------------------------------------------------------
    // Start / end dates
    // -------------------------------------------------------------------------

    /**
     * AC: startDate can be set and retrieved.
     */
    @Test
    public void startDate_setAndGet() {
        CampusGoal goal = makeGoal();
        Timestamp ts = new Timestamp(1_700_000_000L, 0);
        goal.setStartDate(ts);
        assertEquals(ts, goal.getStartDate());
    }

    /**
     * AC: endDate can be set and retrieved.
     */
    @Test
    public void endDate_setAndGet() {
        CampusGoal goal = makeGoal();
        Timestamp ts = new Timestamp(1_720_000_000L, 0);
        goal.setEndDate(ts);
        assertEquals(ts, goal.getEndDate());
    }

    /**
     * AC: The goal's endDate is after its startDate for a valid time range.
     */
    @Test
    public void startAndEndDates_endAfterStart_isValid() {
        CampusGoal goal = makeGoal();
        Timestamp start = new Timestamp(1_700_000_000L, 0);
        Timestamp end = new Timestamp(1_720_000_000L, 0);
        goal.setStartDate(start);
        goal.setEndDate(end);
        assertTrue(goal.getEndDate().toDate().after(goal.getStartDate().toDate()));
    }

    // -------------------------------------------------------------------------
    // notificationMilestones
    // -------------------------------------------------------------------------

    /**
     * AC: Notification milestones provided in the constructor are stored.
     */
    @Test
    public void notificationMilestones_storedFromConstructor() {
        List<Integer> milestones = Arrays.asList(25, 50, 75, 100);
        CampusGoal goal = new CampusGoal(
                "g1", "Title", CampusGoal.METRIC_CO2,
                1000, null, null, milestones
        );
        assertEquals(milestones, goal.getNotificationMilestones());
    }

    /**
     * AC: notificationMilestones can be updated via setter.
     */
    @Test
    public void notificationMilestones_setAndGet() {
        CampusGoal goal = makeGoal();
        goal.setNotificationMilestones(Arrays.asList(10, 20, 30));
        assertEquals(3, goal.getNotificationMilestones().size());
        assertEquals(Integer.valueOf(10), goal.getNotificationMilestones().get(0));
    }

    /**
     * AC: A goal can be created with null notificationMilestones.
     */
    @Test
    public void notificationMilestones_nullMilestones_isAllowed() {
        CampusGoal goal = new CampusGoal(
                "g1", "Title", CampusGoal.METRIC_WASTE,
                500, null, null, null
        );
        assertNull(goal.getNotificationMilestones());
    }

    // -------------------------------------------------------------------------
    // getProgressPercentage — additional cases
    // -------------------------------------------------------------------------

    /**
     * AC: 1 unit progress out of 10000 = 0.01%.
     */
    @Test
    public void getProgressPercentage_oneUnitOutOfTenThousand_returnsPointZeroOne() {
        CampusGoal goal = goalWith(10000, 1);
        assertEquals(0.01, goal.getProgressPercentage(), 1e-9);
    }

    /**
     * AC: Progress value equals targetValue / 4 → 25%.
     */
    @Test
    public void getProgressPercentage_quarterProgress_returnsTwentyFive() {
        CampusGoal goal = goalWith(400, 100);
        assertEquals(25.0, goal.getProgressPercentage(), 1e-9);
    }

    /**
     * AC: Progress value equals 3 × targetValue / 4 → 75%.
     */
    @Test
    public void getProgressPercentage_threeQuarterProgress_returnsSeventyFive() {
        CampusGoal goal = goalWith(400, 300);
        assertEquals(75.0, goal.getProgressPercentage(), 1e-9);
    }

    /**
     * AC: getProgressPercentage returns exactly 0 when progress is exactly 0.
     */
    @Test
    public void getProgressPercentage_zeroProgress_exactlyZero() {
        CampusGoal goal = goalWith(1000, 0);
        assertEquals(0.0, goal.getProgressPercentage(), 0.0); // strict equality
    }

    // -------------------------------------------------------------------------
    // getRemainingValue — additional cases
    // -------------------------------------------------------------------------

    /**
     * AC: getRemainingValue at zero progress equals the full targetValue.
     */
    @Test
    public void getRemainingValue_zeroProgress_equalsTarget() {
        double target = 1500.0;
        CampusGoal goal = goalWith(target, 0);
        assertEquals(target, goal.getRemainingValue(), 1e-9);
    }

    /**
     * AC: getRemainingValue is always non-negative (clamped at 0 when overshot).
     */
    @Test
    public void getRemainingValue_alwaysNonNegative() {
        for (double progress : new double[]{0, 250, 500, 750, 1000, 1500}) {
            CampusGoal goal = goalWith(1000, progress);
            assertTrue(
                    "Remaining value must be >= 0 for progress=" + progress,
                    goal.getRemainingValue() >= 0.0
            );
        }
    }

    // -------------------------------------------------------------------------
    // Status transition
    // -------------------------------------------------------------------------

    /**
     * AC: Status can be updated from ACTIVE → COMPLETED → ARCHIVED.
     */
    @Test
    public void status_fullLifecycle() {
        CampusGoal goal = makeGoal();
        assertEquals(CampusGoal.STATUS_ACTIVE, goal.getStatus());
        goal.setStatus(CampusGoal.STATUS_COMPLETED);
        assertEquals(CampusGoal.STATUS_COMPLETED, goal.getStatus());
        goal.setStatus(CampusGoal.STATUS_ARCHIVED);
        assertEquals(CampusGoal.STATUS_ARCHIVED, goal.getStatus());
    }

    // -------------------------------------------------------------------------
    // metricType transitions
    // -------------------------------------------------------------------------

    /**
     * AC: All three metric types can be set and retrieved.
     */
    @Test
    public void metricType_allTypes_setAndGet() {
        CampusGoal goal = makeGoal();
        goal.setMetricType(CampusGoal.METRIC_CO2);
        assertEquals(CampusGoal.METRIC_CO2, goal.getMetricType());
        goal.setMetricType(CampusGoal.METRIC_WASTE);
        assertEquals(CampusGoal.METRIC_WASTE, goal.getMetricType());
        goal.setMetricType(CampusGoal.METRIC_ENERGY);
        assertEquals(CampusGoal.METRIC_ENERGY, goal.getMetricType());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CampusGoal makeGoal() {
        return new CampusGoal(
                "goal1", "Test Goal", CampusGoal.METRIC_CO2,
                1000, null, null, Arrays.asList(25, 50, 75, 100)
        );
    }

    private CampusGoal goalWith(double target, double progress) {
        CampusGoal goal = makeGoal();
        goal.setTargetValue(target);
        goal.setCurrentProgress(progress);
        return goal;
    }
}
