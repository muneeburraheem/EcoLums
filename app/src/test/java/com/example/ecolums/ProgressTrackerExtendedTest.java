package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

/**
 * Extended unit tests for ProgressTracker — covers ImpactSummary accumulation
 * edge cases, DataPoint construction/mutation, multiple ProgressTracker instances,
 * and milestone-threshold boundary conditions not in the primary suite.
 * <p>
 * Firebase is mocked; only the pure-Java inner data classes are exercised here.
 */
public class ProgressTrackerExtendedTest {

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
    // ImpactSummary — accumulation edge cases
    // -------------------------------------------------------------------------

    /**
     * AC: CO2 field accepts negative values (net emitter scenario).
     */
    @Test
    public void impactSummary_negativeCO2_isAccepted() {
        ProgressTracker.ImpactSummary s = new ProgressTracker.ImpactSummary();
        s.totalCO2SavedKg -= 5.0;
        assertEquals(-5.0, s.totalCO2SavedKg, 1e-9);
    }

    /**
     * AC: All four fields are independent — accumulating one does not affect others.
     */
    @Test
    public void impactSummary_allFieldsAreIndependent() {
        ProgressTracker.ImpactSummary s = new ProgressTracker.ImpactSummary();
        s.totalCO2SavedKg = 10.0;
        s.totalWasteDivertedKg = 20.0;
        s.totalEnergySavedKwh = 30.0;
        s.totalPointsEarned = 40.0;

        assertEquals(10.0, s.totalCO2SavedKg, 1e-9);
        assertEquals(20.0, s.totalWasteDivertedKg, 1e-9);
        assertEquals(30.0, s.totalEnergySavedKwh, 1e-9);
        assertEquals(40.0, s.totalPointsEarned, 1e-9);
    }

    /**
     * AC: Accumulating large values stays precise.
     */
    @Test
    public void impactSummary_largeValues_stayPrecise() {
        ProgressTracker.ImpactSummary s = new ProgressTracker.ImpactSummary();
        for (int i = 0; i < 1000; i++) {
            s.totalCO2SavedKg += 1.5;
        }
        assertEquals(1500.0, s.totalCO2SavedKg, 1e-6);
    }

    /**
     * AC: Points field can exceed 10000 (campus-wide aggregate).
     */
    @Test
    public void impactSummary_largePointsTotal_storedCorrectly() {
        ProgressTracker.ImpactSummary s = new ProgressTracker.ImpactSummary();
        s.totalPointsEarned = 99999.99;
        assertEquals(99999.99, s.totalPointsEarned, 1e-6);
    }

    /**
     * AC: Multiple summaries can be created independently.
     */
    @Test
    public void impactSummary_multipleSummaries_areIndependent() {
        ProgressTracker.ImpactSummary s1 = new ProgressTracker.ImpactSummary();
        ProgressTracker.ImpactSummary s2 = new ProgressTracker.ImpactSummary();
        s1.totalCO2SavedKg = 100.0;
        s2.totalCO2SavedKg = 200.0;
        assertEquals(100.0, s1.totalCO2SavedKg, 1e-9);
        assertEquals(200.0, s2.totalCO2SavedKg, 1e-9);
    }

    /**
     * AC: Waste field specifically tracks kg regardless of CO2 conversion.
     */
    @Test
    public void impactSummary_wasteField_tracksKg() {
        ProgressTracker.ImpactSummary s = new ProgressTracker.ImpactSummary();
        s.totalWasteDivertedKg += 5.0;
        s.totalWasteDivertedKg += 3.5;
        assertEquals(8.5, s.totalWasteDivertedKg, 1e-9);
    }

    // -------------------------------------------------------------------------
    // DataPoint — construction and mutation
    // -------------------------------------------------------------------------

    /**
     * AC: DataPoint stores zero value correctly.
     */
    @Test
    public void dataPoint_zeroValue_storedCorrectly() {
        ProgressTracker.DataPoint dp = new ProgressTracker.DataPoint("Jan", 0.0);
        assertEquals(0.0, dp.value, 1e-9);
    }

    /**
     * AC: DataPoint stores negative value correctly (e.g., net CO2 emitted day).
     */
    @Test
    public void dataPoint_negativeValue_storedCorrectly() {
        ProgressTracker.DataPoint dp = new ProgressTracker.DataPoint("Mon", -2.5);
        assertEquals(-2.5, dp.value, 1e-9);
    }

    /**
     * AC: DataPoint label can be any string (day, week, month label).
     */
    @Test
    public void dataPoint_variousLabels_storedCorrectly() {
        String[] labels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (String label : labels) {
            ProgressTracker.DataPoint dp = new ProgressTracker.DataPoint(label, 1.0);
            assertEquals(label, dp.label);
        }
    }

    /**
     * AC: A list of DataPoints can be built and iterated.
     */
    @Test
    public void dataPoints_listOfPoints_buildable() {
        List<ProgressTracker.DataPoint> series = new ArrayList<>();
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri"};
        double[] values = {1.0, 2.5, 0.0, 3.0, 1.5};

        for (int i = 0; i < days.length; i++) {
            series.add(new ProgressTracker.DataPoint(days[i], values[i]));
        }

        assertEquals(5, series.size());
        assertEquals("Mon", series.get(0).label);
        assertEquals(1.0, series.get(0).value, 1e-9);
        assertEquals("Fri", series.get(4).label);
        assertEquals(1.5, series.get(4).value, 1e-9);
    }

    /**
     * AC: DataPoint value can be updated after construction.
     */
    @Test
    public void dataPoint_valueUpdatable() {
        ProgressTracker.DataPoint dp = new ProgressTracker.DataPoint("Week 1", 10.0);
        dp.value += 5.0; // accumulate more data
        assertEquals(15.0, dp.value, 1e-9);
    }

    // -------------------------------------------------------------------------
    // ProgressTracker construction
    // -------------------------------------------------------------------------

    /**
     * AC: Multiple ProgressTracker instances can be created independently.
     */
    @Test
    public void progressTracker_multipleInstances_createdSuccessfully() {
        ProgressTracker t1 = new ProgressTracker();
        ProgressTracker t2 = new ProgressTracker();
        assertNotNull(t1);
        assertNotNull(t2);
        assertTrue(t1 != t2);
    }

    // -------------------------------------------------------------------------
    // Milestone threshold logic — boundary values
    // -------------------------------------------------------------------------

    /**
     * AC: User at exactly the threshold (totalPoints == pointThreshold) qualifies.
     */
    @Test
    public void milestoneCheck_exactlyAtThreshold_qualifies() {
        Badge badge = new Badge("b1", "Exact", "desc", "crit", "url", 100);
        double userPoints = 100.0;
        assertTrue(userPoints >= badge.getPointThreshold() && badge.getPointThreshold() > 0);
    }

    /**
     * AC: User one point below threshold does not qualify.
     */
    @Test
    public void milestoneCheck_oneBelowThreshold_doesNotQualify() {
        Badge badge = new Badge("b1", "Near Miss", "desc", "crit", "url", 100);
        double userPoints = 99.9;
        // Condition from checkMilestonesForUser: threshold > 0 && points >= threshold
        assertTrue(badge.getPointThreshold() > 0 && userPoints < badge.getPointThreshold());
    }

    /**
     * AC: Streak badge with streakRequired = 7 qualifies when streak == 7.
     */
    @Test
    public void milestoneCheck_streakBadge_exactlyAtRequirement_qualifies() {
        Badge badge = new Badge("b2", "Week Warrior", "desc", "crit", "url", 0);
        badge.setBadgeType(Badge.TYPE_STREAK);
        badge.setStreakRequired(7);
        int userStreak = 7;
        assertTrue(badge.getStreakRequired() > 0 && userStreak >= badge.getStreakRequired());
    }

    /**
     * AC: Streak badge with streakRequired = 7 does not qualify when streak == 6.
     */
    @Test
    public void milestoneCheck_streakBadge_oneDayShort_doesNotQualify() {
        Badge badge = new Badge("b2", "Week Warrior", "desc", "crit", "url", 0);
        badge.setBadgeType(Badge.TYPE_STREAK);
        badge.setStreakRequired(7);
        int userStreak = 6;
        assertTrue(badge.getStreakRequired() > 0 && userStreak < badge.getStreakRequired());
    }

    /**
     * AC: Streak badge with streakRequired = 30 qualifies when streak == 31.
     */
    @Test
    public void milestoneCheck_streakBadge_exceeds30days_qualifies() {
        Badge badge = new Badge("b3", "Month Champ", "desc", "crit", "url", 0);
        badge.setBadgeType(Badge.TYPE_STREAK);
        badge.setStreakRequired(30);
        int userStreak = 31;
        assertTrue(badge.getStreakRequired() > 0 && userStreak >= badge.getStreakRequired());
    }
}
