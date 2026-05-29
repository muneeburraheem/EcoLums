package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Calendar;

/**
 * Unit tests for StreakManager — covers US_02.03 (Daily Streak Tracking).
 * <p>
 * StreakManager is a static utility. All Firebase I/O is mocked; the tests
 * verify only the in-memory streak state on the {@link User} object after
 * a call to {@link StreakManager#updateStreak}.
 * <p>
 * Note: the "consecutive day" and "gap" tests rely on calendar arithmetic.
 * They will always pass unless run exactly at midnight on December 31st/January 1st,
 * where the year-boundary edge case would be exercised differently.
 */
@SuppressWarnings("unchecked")
public class StreakManagerTest {

    private MockedStatic<FirebaseFirestore> mockedFirestore;
    private FirebaseFirestore mockDb;

    @Before
    public void setUp() {
        mockDb = mock(FirebaseFirestore.class);
        mockedFirestore = Mockito.mockStatic(FirebaseFirestore.class);
        mockedFirestore.when(FirebaseFirestore::getInstance).thenReturn(mockDb);

        // Common mock chain for users collection (fire-and-forget update)
        CollectionReference mockUsersCol = mock(CollectionReference.class);
        DocumentReference mockUserDoc = mock(DocumentReference.class);
        Task<Void> mockVoidTask = mock(Task.class);
        when(mockDb.collection("users")).thenReturn(mockUsersCol);
        when(mockUsersCol.document(anyString())).thenReturn(mockUserDoc);
        when(mockUserDoc.update(anyString(), any(), anyString(), any())).thenReturn(mockVoidTask);

        // Mock chain for badges collection (used by checkStreakBadges inside ProgressTracker)
        CollectionReference mockBadgesCol = mock(CollectionReference.class);
        Query mockQuery = mock(Query.class);
        Task<QuerySnapshot> mockQueryTask = mock(Task.class);
        when(mockDb.collection("badges")).thenReturn(mockBadgesCol);
        when(mockBadgesCol.whereEqualTo(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.get()).thenReturn(mockQueryTask);
        when(mockQueryTask.addOnSuccessListener(any())).thenReturn(mockQueryTask);
        when(mockQueryTask.addOnFailureListener(any())).thenReturn(mockQueryTask);
    }

    @After
    public void tearDown() {
        mockedFirestore.close();
    }

    // -------------------------------------------------------------------------
    // Guard conditions
    // -------------------------------------------------------------------------

    /**
     * AC: Passing null as the user must not throw — the method returns immediately.
     */
    @Test
    public void updateStreak_nullUser_doesNotThrow() {
        StreakManager.updateStreak(null); // must not throw
    }

    /**
     * AC: A user with a null userId must not throw — the method returns immediately.
     */
    @Test
    public void updateStreak_nullUserId_doesNotThrow() {
        User user = new User();
        user.setUserId(null);
        StreakManager.updateStreak(user); // must not throw
    }

    /**
     * AC: A user with a null userId leaves streak unchanged (at 0).
     */
    @Test
    public void updateStreak_nullUserId_doesNotChangeStreak() {
        User user = new User();
        user.setUserId(null);
        user.setCurrentStreak(7);
        StreakManager.updateStreak(user);
        assertEquals(7, user.getCurrentStreak()); // unchanged
    }

    // -------------------------------------------------------------------------
    // First-ever activity (lastActivityDate == null)
    // -------------------------------------------------------------------------

    /**
     * AC: When a user logs their very first activity (last == null),
     * currentStreak is set to 1.
     */
    @Test
    public void updateStreak_noLastActivity_setsStreakToOne() {
        User user = makeUser();
        user.setLastActivityDate(null);
        user.setCurrentStreak(0);

        StreakManager.updateStreak(user);

        assertEquals(1, user.getCurrentStreak());
    }

    /**
     * AC: After the first activity, lastActivityDate is no longer null.
     */
    @Test
    public void updateStreak_noLastActivity_updatesLastActivityDateToNonNull() {
        User user = makeUser();
        user.setLastActivityDate(null);

        StreakManager.updateStreak(user);

        assertNotNull(user.getLastActivityDate());
    }

    /**
     * AC: A pre-existing streak of 5 is overridden to 1 on the very first
     * activity (this handles a corrupt/default state where streak > 0 but
     * lastActivityDate is null).
     */
    @Test
    public void updateStreak_noLastActivityButStreakPreset_resetsToOne() {
        User user = makeUser();
        user.setLastActivityDate(null);
        user.setCurrentStreak(5); // inconsistent but shouldn't matter

        StreakManager.updateStreak(user);

        assertEquals(1, user.getCurrentStreak());
    }

    // -------------------------------------------------------------------------
    // Same-day activity (streak stays the same)
    // -------------------------------------------------------------------------

    /**
     * AC: When the user already logged an activity today, the streak must
     * not be incremented or reset.
     */
    @Test
    public void updateStreak_sameDay_doesNotChangeStreak() {
        User user = makeUser();
        user.setCurrentStreak(4);
        // Set lastActivityDate to a few seconds ago (same calendar day)
        long nowSeconds = System.currentTimeMillis() / 1000;
        user.setLastActivityDate(new Timestamp(nowSeconds - 10, 0));

        StreakManager.updateStreak(user);

        assertEquals(4, user.getCurrentStreak()); // unchanged
    }

    // -------------------------------------------------------------------------
    // Consecutive-day activity (streak increments)
    // -------------------------------------------------------------------------

    /**
     * AC: Logging an activity on the day after the previous activity
     * increments the streak by 1.
     */
    @Test
    public void updateStreak_consecutiveDay_incrementsStreakByOne() {
        User user = makeUser();
        user.setCurrentStreak(3);
        user.setLastActivityDate(yesterdayNoon());

        StreakManager.updateStreak(user);

        assertEquals(4, user.getCurrentStreak());
    }

    /**
     * AC: Starting from a streak of 1, consecutive logging produces streak = 2.
     */
    @Test
    public void updateStreak_consecutiveDay_fromStreakOne_becomesTwo() {
        User user = makeUser();
        user.setCurrentStreak(1);
        user.setLastActivityDate(yesterdayNoon());

        StreakManager.updateStreak(user);

        assertEquals(2, user.getCurrentStreak());
    }

    /**
     * AC: After a consecutive-day update, lastActivityDate is updated to today.
     */
    @Test
    public void updateStreak_consecutiveDay_updatesLastActivityDate() {
        User user = makeUser();
        user.setLastActivityDate(yesterdayNoon());

        StreakManager.updateStreak(user);

        assertNotNull(user.getLastActivityDate());
        // The updated date should be newer than yesterday noon
        assertTrue(
                "lastActivityDate should be updated to today",
                user.getLastActivityDate().toDate().after(yesterdayNoon().toDate())
        );
    }

    // -------------------------------------------------------------------------
    // Gap in activity (streak resets to 1)
    // -------------------------------------------------------------------------

    /**
     * AC: Logging an activity after a 2-day gap resets the streak to 1.
     */
    @Test
    public void updateStreak_twoDayGap_resetsStreakToOne() {
        User user = makeUser();
        user.setCurrentStreak(10);
        user.setLastActivityDate(daysAgoNoon(2));

        StreakManager.updateStreak(user);

        assertEquals(1, user.getCurrentStreak());
    }

    /**
     * AC: A 3-day gap also resets to 1.
     */
    @Test
    public void updateStreak_threeDayGap_resetsStreakToOne() {
        User user = makeUser();
        user.setCurrentStreak(7);
        user.setLastActivityDate(daysAgoNoon(3));

        StreakManager.updateStreak(user);

        assertEquals(1, user.getCurrentStreak());
    }

    /**
     * AC: A 30-day gap also resets to 1 (long idle period).
     */
    @Test
    public void updateStreak_thirtyDayGap_resetsStreakToOne() {
        User user = makeUser();
        user.setCurrentStreak(30);
        user.setLastActivityDate(daysAgoNoon(30));

        StreakManager.updateStreak(user);

        assertEquals(1, user.getCurrentStreak());
    }

    /**
     * AC: After a gap reset, lastActivityDate is updated to today.
     */
    @Test
    public void updateStreak_gapReset_updatesLastActivityDate() {
        User user = makeUser();
        Timestamp twoDaysAgo = daysAgoNoon(2);
        user.setLastActivityDate(twoDaysAgo);

        StreakManager.updateStreak(user);

        assertNotNull(user.getLastActivityDate());
        assertTrue(
                "lastActivityDate should be updated",
                user.getLastActivityDate().toDate().after(twoDaysAgo.toDate())
        );
    }

    /**
     * AC: A streak of 1 that is interrupted by a gap resets to 1 (no visible change,
     * but confirms the reset branch was taken rather than the increment branch).
     */
    @Test
    public void updateStreak_streakOneWithGap_remainsOne() {
        User user = makeUser();
        user.setCurrentStreak(1);
        user.setLastActivityDate(daysAgoNoon(5));

        StreakManager.updateStreak(user);

        assertEquals(1, user.getCurrentStreak());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User makeUser() {
        User user = new User("uid-streak", "Streak User", "streak@lums.edu.pk", User.ROLE_STUDENT);
        return user;
    }

    /**
     * Returns a {@link Timestamp} for noon on the previous calendar day.
     */
    private Timestamp yesterdayNoon() {
        return daysAgoNoon(1);
    }

    /**
     * Returns a {@link Timestamp} for noon {@code daysBack} calendar days ago.
     */
    private Timestamp daysAgoNoon(int daysBack) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -daysBack);
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new Timestamp(cal.getTimeInMillis() / 1000, 0);
    }

    private void assertTrue(String message, boolean condition) {
        org.junit.Assert.assertTrue(message, condition);
    }
}
