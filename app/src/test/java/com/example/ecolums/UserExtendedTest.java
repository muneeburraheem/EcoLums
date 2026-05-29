package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Extended unit tests for User — covers leaveClub guard conditions,
 * awardBadge duplicate detection, role constants, profile setters, and
 * streak-related fields not covered by the primary UserTest suite.
 * <p>
 * Firebase is mocked; all tested paths are either synchronous or involve
 * only the in-memory guard conditions.
 */
@SuppressWarnings("unchecked")
public class UserExtendedTest {

    private MockedStatic<FirebaseFirestore> mockedFirestore;

    @Before
    public void setUp() {
        FirebaseFirestore mockDb = mock(FirebaseFirestore.class);
        mockedFirestore = Mockito.mockStatic(FirebaseFirestore.class);
        mockedFirestore.when(FirebaseFirestore::getInstance).thenReturn(mockDb);

        // Full Firestore chain for addPoints / awardBadge / leaveClub paths
        CollectionReference mockUsersCol = mock(CollectionReference.class);
        DocumentReference mockUserDoc = mock(DocumentReference.class);
        CollectionReference mockSubCol = mock(CollectionReference.class);
        DocumentReference mockSubDoc = mock(DocumentReference.class);
        Task<Void> mockVoidTask = mock(Task.class);
        WriteBatch mockBatch = mock(WriteBatch.class);

        when(mockDb.collection(anyString())).thenReturn(mockUsersCol);
        when(mockUsersCol.document(anyString())).thenReturn(mockUserDoc);
        when(mockUserDoc.update(anyString(), any())).thenReturn(mockVoidTask);
        when(mockUserDoc.collection(anyString())).thenReturn(mockSubCol);
        when(mockSubCol.document(anyString())).thenReturn(mockSubDoc);
        when(mockVoidTask.addOnSuccessListener(any())).thenReturn(mockVoidTask);
        when(mockVoidTask.addOnFailureListener(any())).thenReturn(mockVoidTask);

        // WriteBatch chain for awardBadge / leaveClub
        when(mockDb.batch()).thenReturn(mockBatch);
        when(mockBatch.set(any(DocumentReference.class), any(Map.class))).thenReturn(mockBatch);
        when(mockBatch.update(any(DocumentReference.class), anyString(), any())).thenReturn(mockBatch);
        when(mockBatch.commit()).thenReturn(mockVoidTask);
    }

    @After
    public void tearDown() {
        mockedFirestore.close();
    }

    // -------------------------------------------------------------------------
    // leaveClub — guard conditions
    // -------------------------------------------------------------------------

    /**
     * AC: leaveClub returns false immediately when the user is not in any club
     * (clubId == null).
     */
    @Test
    public void leaveClub_whenNotInClub_callsCallbackWithFalse() {
        User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
        // clubId is null by default
        AtomicBoolean result = new AtomicBoolean(true);
        user.leaveClub(success -> result.set(success));
        assertFalse(result.get());
    }

    /**
     * AC: After leaving a club (clubId set to null), a subsequent leaveClub
     * call also returns false.
     */
    @Test
    public void leaveClub_afterAlreadyLeft_callsCallbackWithFalse() {
        User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
        user.setClubId(null); // explicitly null
        AtomicBoolean result = new AtomicBoolean(true);
        user.leaveClub(success -> result.set(success));
        assertFalse(result.get());
    }

    // -------------------------------------------------------------------------
    // awardBadge — duplicate detection (synchronous)
    // -------------------------------------------------------------------------

    /**
     * AC: Awarding a badge that the user has already earned calls the callback
     * with {@code true} immediately (no Firestore write needed).
     */
    @Test
    public void awardBadge_alreadyEarned_callsCallbackWithTrue() {
        User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
        Badge badge = new Badge("b1", "Test", "desc", "criteria", "🌿", 100);

        // Manually add the badge ID to the earned list
        user.getEarnedBadgeIds().add("b1");

        AtomicBoolean result = new AtomicBoolean(false);
        user.awardBadge(badge, success -> result.set(success));
        assertTrue(result.get());
    }

    /**
     * AC: Awarding a second different badge after the first is not blocked.
     */
    @Test
    public void awardBadge_differentBadge_notBlocked() {
        User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
        user.getEarnedBadgeIds().add("b1");
        Badge badge = new Badge("b2", "Other", "desc", "criteria", "🌿", 200);

        // b2 is NOT in earned list — the method should proceed to Firestore (not return early)
        // We just verify it didn't return the "already earned" true synchronously
        AtomicBoolean called = new AtomicBoolean(false);
        user.awardBadge(badge, success -> called.set(true));
        // The mock doesn't fire success listeners, so called should remain false
        // This just confirms no early-exit path was taken that would set it
        // We verify earnedBadgeIds is locally updated (pre-Firestore commit)
        assertTrue("Badge b2 should be added locally", user.getEarnedBadgeIds().contains("b2"));
    }

    /**
     * AC: awardBadge initialises earnedBadgeIds if it is null (defensive path).
     */
    @Test
    public void awardBadge_nullEarnedBadgeIds_initializesListAndProceed() {
        User user = new User();
        user.setUserId("uid");
        user.setEarnedBadgeIds(null); // force null list
        Badge badge = new Badge("b99", "Test", "d", "c", "🌿", 0);

        // Should not throw NullPointerException
        user.awardBadge(badge, success -> {});
    }

    // -------------------------------------------------------------------------
    // Role constants
    // -------------------------------------------------------------------------

    @Test
    public void roleConstant_student_hasCorrectValue() {
        assertEquals("STUDENT", User.ROLE_STUDENT);
    }

    @Test
    public void roleConstant_clubLeader_hasCorrectValue() {
        assertEquals("CLUB_LEADER", User.ROLE_CLUB_LEADER);
    }

    @Test
    public void roleConstant_admin_hasCorrectValue() {
        assertEquals("ADMIN", User.ROLE_ADMIN);
    }

    /**
     * AC: All three role constants are distinct.
     */
    @Test
    public void roleConstants_areAllDistinct() {
        assertFalse(User.ROLE_STUDENT.equals(User.ROLE_CLUB_LEADER));
        assertFalse(User.ROLE_STUDENT.equals(User.ROLE_ADMIN));
        assertFalse(User.ROLE_CLUB_LEADER.equals(User.ROLE_ADMIN));
    }

    // -------------------------------------------------------------------------
    // Profile field getters / setters
    // -------------------------------------------------------------------------

    /**
     * AC: setName / getName round-trip.
     */
    @Test
    public void name_setAndGet() {
        User user = new User("uid", "Old Name", "e@lums.edu.pk", User.ROLE_STUDENT);
        user.setName("New Name");
        assertEquals("New Name", user.getName());
    }

    /**
     * AC: setEmail / getEmail round-trip.
     */
    @Test
    public void email_setAndGet() {
        User user = new User();
        user.setEmail("new@lums.edu.pk");
        assertEquals("new@lums.edu.pk", user.getEmail());
    }

    /**
     * AC: setUserId / getUserId round-trip.
     */
    @Test
    public void userId_setAndGet() {
        User user = new User();
        user.setUserId("new-uid-123");
        assertEquals("new-uid-123", user.getUserId());
    }

    /**
     * AC: setProfileImageUrl / getProfileImageUrl round-trip.
     */
    @Test
    public void profileImageUrl_setAndGet() {
        User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
        user.setProfileImageUrl("https://storage.googleapis.com/avatar.jpg");
        assertEquals("https://storage.googleapis.com/avatar.jpg", user.getProfileImageUrl());
    }

    /**
     * AC: setShareDataWithClubs can be toggled on and off.
     */
    @Test
    public void shareDataWithClubs_togglesCorrectly() {
        User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
        assertFalse(user.isShareDataWithClubs());
        user.setShareDataWithClubs(true);
        assertTrue(user.isShareDataWithClubs());
        user.setShareDataWithClubs(false);
        assertFalse(user.isShareDataWithClubs());
    }

    /**
     * AC: setClubId / getClubId round-trip.
     */
    @Test
    public void clubId_setAndGet() {
        User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
        user.setClubId("club-abc");
        assertEquals("club-abc", user.getClubId());
    }

    /**
     * AC: setCurrentStreak / getCurrentStreak round-trip.
     */
    @Test
    public void currentStreak_setAndGet() {
        User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
        user.setCurrentStreak(14);
        assertEquals(14, user.getCurrentStreak());
    }

    /**
     * AC: lastActivityDate starts null in the 4-arg constructor.
     */
    @Test
    public void lastActivityDate_defaultNull() {
        User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
        assertNull(user.getLastActivityDate());
    }

    /**
     * AC: createdAt is non-null after the 4-arg constructor (set to Timestamp.now()).
     */
    @Test
    public void createdAt_nonNullAfterConstruction() {
        User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
        assertNotNull(user.getCreatedAt());
    }

    /**
     * AC: No-arg constructor does not throw; all fields are null/default.
     */
    @Test
    public void noArgConstructor_doesNotThrow() {
        User user = new User();
        assertNull(user.getUserId());
        assertNull(user.getName());
        assertEquals(0.0, user.getTotalPoints(), 1e-9);
    }

    /**
     * AC: setTotalPoints directly sets the field independently of addPoints.
     */
    @Test
    public void setTotalPoints_updatesFieldDirectly() {
        User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
        user.setTotalPoints(999.0);
        assertEquals(999.0, user.getTotalPoints(), 1e-9);
    }

    /**
     * AC: setRole changes the role.
     */
    @Test
    public void setRole_updatesRole() {
        User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
        user.setRole(User.ROLE_ADMIN);
        assertEquals(User.ROLE_ADMIN, user.getRole());
    }
}
