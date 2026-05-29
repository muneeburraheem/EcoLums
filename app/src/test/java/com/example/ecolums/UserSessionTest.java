package com.example.ecolums;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Unit tests for UserSession — covers US_01.01 (Authentication / session management)
 * and Role-Based Access Control checks.
 * <p>
 * UserSession is a singleton that holds the logged-in User in memory.
 * Firebase is mocked only so that User constructors don't throw; the
 * session logic itself requires no Firestore calls.
 */
public class UserSessionTest {

	private MockedStatic<FirebaseFirestore> mockedFirestore;

	@Before
	public void setUp() {
		FirebaseFirestore mockDb = mock(FirebaseFirestore.class);
		mockedFirestore = Mockito.mockStatic(FirebaseFirestore.class);
		mockedFirestore.when(FirebaseFirestore::getInstance).thenReturn(mockDb);
		// Reset singleton state between tests
		UserSession.getInstance().clear();
	}

	@After
	public void tearDown() {
		mockedFirestore.close();
	}

	// -------------------------------------------------------------------------
	// US_01.01 — Singleton contract
	// -------------------------------------------------------------------------

	/**
	 * AC: getInstance() always returns the same object.
	 */
	@Test
	public void getInstance_returnsSameInstance() {
		UserSession a = UserSession.getInstance();
		UserSession b = UserSession.getInstance();
		assertSame(a, b);
	}

	/**
	 * AC: Repeated calls all return the same instance.
	 */
	@Test
	public void getInstance_consistentAcrossMultipleCalls() {
		UserSession first = UserSession.getInstance();
		for (int i = 0; i < 10; i++) {
			assertSame(first, UserSession.getInstance());
		}
	}

	// -------------------------------------------------------------------------
	// US_01.01 — Login state
	// -------------------------------------------------------------------------

	/**
	 * AC: No user set → isLoggedIn() returns false.
	 */
	@Test
	public void isLoggedIn_noUser_returnsFalse() {
		assertFalse(UserSession.getInstance().isLoggedIn());
	}

	/**
	 * AC: User set → isLoggedIn() returns true.
	 */
	@Test
	public void isLoggedIn_withUser_returnsTrue() {
		UserSession.getInstance().setCurrentUser(userWithRole(User.ROLE_STUDENT));
		assertTrue(UserSession.getInstance().isLoggedIn());
	}

	/**
	 * AC: getCurrentUser() returns exactly the user that was set.
	 */
	@Test
	public void getCurrentUser_returnsSetUser() {
		User user = userWithRole(User.ROLE_STUDENT);
		UserSession.getInstance().setCurrentUser(user);
		assertSame(user, UserSession.getInstance().getCurrentUser());
	}

	// -------------------------------------------------------------------------
	// US_01.01 — Sign-out / clear
	// -------------------------------------------------------------------------

	/**
	 * AC: clear() removes the current user.
	 */
	@Test
	public void clear_removesCurrentUser() {
		UserSession.getInstance().setCurrentUser(userWithRole(User.ROLE_STUDENT));
		UserSession.getInstance().clear();
		assertNull(UserSession.getInstance().getCurrentUser());
	}

	/**
	 * AC: After clear(), isLoggedIn() returns false.
	 */
	@Test
	public void clear_isLoggedInReturnsFalse() {
		UserSession.getInstance().setCurrentUser(userWithRole(User.ROLE_STUDENT));
		UserSession.getInstance().clear();
		assertFalse(UserSession.getInstance().isLoggedIn());
	}

	// -------------------------------------------------------------------------
	// RBAC — isAdmin() checks
	// -------------------------------------------------------------------------

	/**
	 * AC: No user → isAdmin() returns false.
	 */
	@Test
	public void isAdmin_noUser_returnsFalse() {
		assertFalse(UserSession.getInstance().isAdmin());
	}

	/**
	 * AC: STUDENT role → isAdmin() returns false.
	 */
	@Test
	public void isAdmin_studentUser_returnsFalse() {
		UserSession.getInstance().setCurrentUser(userWithRole(User.ROLE_STUDENT));
		assertFalse(UserSession.getInstance().isAdmin());
	}

	/**
	 * AC: CLUB_LEADER role → isAdmin() returns false.
	 */
	@Test
	public void isAdmin_clubLeaderUser_returnsFalse() {
		UserSession.getInstance().setCurrentUser(userWithRole(User.ROLE_CLUB_LEADER));
		assertFalse(UserSession.getInstance().isAdmin());
	}

	/**
	 * AC: ADMIN role → isAdmin() returns true.
	 */
	@Test
	public void isAdmin_adminUser_returnsTrue() {
		UserSession.getInstance().setCurrentUser(userWithRole(User.ROLE_ADMIN));
		assertTrue(UserSession.getInstance().isAdmin());
	}

	/**
	 * AC: Replacing admin user with a student clears admin status.
	 */
	@Test
	public void isAdmin_afterReplacingAdminWithStudent_returnsFalse() {
		UserSession.getInstance().setCurrentUser(userWithRole(User.ROLE_ADMIN));
		UserSession.getInstance().setCurrentUser(userWithRole(User.ROLE_STUDENT));
		assertFalse(UserSession.getInstance().isAdmin());
	}

	// -------------------------------------------------------------------------
	// Helper
	// -------------------------------------------------------------------------

	/**
	 * Creates a User using setters only (after no-arg constructor).
	 * The no-arg constructor calls FirebaseFirestore.getInstance(), which is
	 * already mocked in setUp().
	 */
	private User userWithRole(String role) {
		User user = new User();
		user.setUserId("uid1");
		user.setName("Test User");
		user.setEmail("test@lums.edu.pk");
		user.setRole(role);
		return user;
	}
}
