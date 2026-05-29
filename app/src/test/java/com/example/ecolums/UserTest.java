package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unit tests for User — covers US_01.02 (User Profile Management),
 * club-membership validation, challenge participation, and badge awarding.
 * <p>
 * Firebase is mocked so that constructors and addPoints() don't throw.
 * Tests focus on business logic with early-exit paths and local state changes.
 */
@SuppressWarnings("unchecked")
public class UserTest {

	private MockedStatic<FirebaseFirestore> mockedFirestore;
	private FirebaseFirestore mockDb;

	@Before
	public void setUp() {
		mockDb = mock(FirebaseFirestore.class);
		mockedFirestore = Mockito.mockStatic(FirebaseFirestore.class);
		mockedFirestore.when(FirebaseFirestore::getInstance).thenReturn(mockDb);

		// Minimal Firestore chain to prevent NPE in addPoints()
		CollectionReference mockUsersCol = mock(CollectionReference.class);
		DocumentReference mockUserDoc = mock(DocumentReference.class);
		Task<Void> mockVoidTask = mock(Task.class);

		when(mockDb.collection("users")).thenReturn(mockUsersCol);
		when(mockUsersCol.document(anyString())).thenReturn(mockUserDoc);
		when(mockUserDoc.update(anyString(), any())).thenReturn(mockVoidTask);
		when(mockVoidTask.addOnSuccessListener(any())).thenReturn(mockVoidTask);
		when(mockVoidTask.addOnFailureListener(any())).thenReturn(mockVoidTask);
	}

	@After
	public void tearDown() {
		mockedFirestore.close();
	}

	// -------------------------------------------------------------------------
	// US_01.02 — User creation defaults
	// -------------------------------------------------------------------------

	/**
	 * AC: New user starts with totalPoints = 0.
	 */
	@Test
	public void constructor_setsDefaultTotalPointsToZero() {
		User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
		assertEquals(0.0, user.getTotalPoints(), 1e-9);
	}

	/**
	 * AC: New user starts with shareDataWithClubs = false.
	 */
	@Test
	public void constructor_setsDefaultShareDataToFalse() {
		User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
		assertFalse(user.isShareDataWithClubs());
	}

	/**
	 * AC: New user has an empty (non-null) earnedBadgeIds list.
	 */
	@Test
	public void constructor_initializesEmptyBadgeList() {
		User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
		assertNotNull(user.getEarnedBadgeIds());
		assertTrue(user.getEarnedBadgeIds().isEmpty());
	}

	/**
	 * AC: Role provided at construction time is stored.
	 */
	@Test
	public void constructor_storesRole() {
		User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_CLUB_LEADER);
		assertEquals(User.ROLE_CLUB_LEADER, user.getRole());
	}

	/**
	 * AC: Email provided at construction time is stored.
	 */
	@Test
	public void constructor_storesEmail() {
		User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
		assertEquals("ali@lums.edu.pk", user.getEmail());
	}

	// -------------------------------------------------------------------------
	// US_01.02 — Points (local state)
	// -------------------------------------------------------------------------

	/**
	 * AC: addPoints() increments totalPoints locally before the Firestore write.
	 * The Firestore chain is mocked so the call doesn't throw.
	 */
	@Test
	public void addPoints_updatesLocalTotalPoints() {
		User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
		user.addPoints(50.0, success -> {});
		assertEquals(50.0, user.getTotalPoints(), 1e-9);
	}

	/**
	 * AC: Multiple addPoints() calls accumulate correctly.
	 */
	@Test
	public void addPoints_multipleCalls_accumulate() {
		User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
		user.addPoints(30.0, s -> {});
		user.addPoints(20.0, s -> {});
		assertEquals(50.0, user.getTotalPoints(), 1e-9);
	}

	// -------------------------------------------------------------------------
	// US_03.02 — Club membership validation
	// -------------------------------------------------------------------------

	/**
	 * AC: joinClubWithCode() returns false immediately when the user is
	 * already in a club (no Firestore query is needed).
	 */
	@Test
	public void joinClubWithCode_whenAlreadyInClub_returnsFalseImmediately() {
		User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
		user.setClubId("existing-club-id");

		AtomicBoolean result = new AtomicBoolean(true);
		user.joinClubWithCode("CODE1", success -> result.set(success));

		assertFalse("Should return false when user is already in a club", result.get());
	}

	/**
	 * AC: leaveClub() returns false immediately when the user has no club.
	 */
	@Test
	public void leaveClub_whenNotInClub_returnsFalseImmediately() {
		User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
		// clubId is null by default

		AtomicBoolean result = new AtomicBoolean(true);
		user.leaveClub(success -> result.set(success));

		assertFalse("Should return false when user has no club", result.get());
	}

	// -------------------------------------------------------------------------
	// US_06.01 — Badge awarding (already-earned early exit)
	// -------------------------------------------------------------------------

	/**
	 * AC: awardBadge() calls callback with true immediately (without touching
	 * Firestore) when the badge is already in the user's earnedBadgeIds list.
	 */
	@Test
	public void awardBadge_whenAlreadyEarned_callsCallbackTrueImmediately() {
		User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
		user.getEarnedBadgeIds().add("badge-1");

		Badge badge = new Badge(
				"badge-1", "Cycle Champion", "desc", "criteria",
				"https://img.png", 100
		);

		AtomicBoolean result = new AtomicBoolean(false);
		user.awardBadge(badge, success -> result.set(success));

		assertTrue("Callback should receive true for already-earned badge", result.get());
	}

	/**
	 * AC: awardBadge() does NOT add a duplicate entry to earnedBadgeIds when
	 * the badge is already present.
	 */
	@Test
	public void awardBadge_alreadyEarned_doesNotAddDuplicate() {
		User user = new User("uid", "Ali", "ali@lums.edu.pk", User.ROLE_STUDENT);
		user.getEarnedBadgeIds().add("badge-1");
		int originalSize = user.getEarnedBadgeIds().size();

		Badge badge = new Badge(
				"badge-1", "Cycle Champion", "desc", "criteria",
				"https://img.png", 100
		);
		user.awardBadge(badge, s -> {});

		assertEquals(originalSize, user.getEarnedBadgeIds().size());
	}

	// -------------------------------------------------------------------------
	// Constants
	// -------------------------------------------------------------------------

	/**
	 * AC: Role constants match the strings stored in Firestore.
	 */
	@Test
	public void roleConstants_haveCorrectValues() {
		assertEquals("STUDENT", User.ROLE_STUDENT);
		assertEquals("CLUB_LEADER", User.ROLE_CLUB_LEADER);
		assertEquals("ADMIN", User.ROLE_ADMIN);
	}
}
