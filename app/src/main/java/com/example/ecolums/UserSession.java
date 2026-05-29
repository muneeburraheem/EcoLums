package com.example.ecolums;

/**
 * Singleton that holds the currently signed-in User object in memory
 * so fragments can access it without extra Firestore reads.
 * <p>
 * Call UserSession.getInstance().setCurrentUser(user) after login.
 * Call UserSession.getInstance().getCurrentUser() anywhere in the app.
 */
public class UserSession {

	private static UserSession instance;
	private User currentUser;

	private UserSession() {
	}

	public static UserSession getInstance() {
		if (instance == null) instance = new UserSession();
		return instance;
	}

	public User getCurrentUser() {
		return currentUser;
	}

	public void setCurrentUser(User user) {
		this.currentUser = user;
	}

	public void clear() {
		currentUser = null;
	}

	/**
	 * Convenience: returns true if a user is signed in AND their User object is loaded.
	 */
	public boolean isLoggedIn() {
		return currentUser != null;
	}

	/**
	 * Convenience: returns true if current user has ADMIN role.
	 */
	public boolean isAdmin() {
		return currentUser != null && User.ROLE_ADMIN.equals(currentUser.getRole());
	}
}
