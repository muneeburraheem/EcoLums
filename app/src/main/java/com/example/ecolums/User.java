package com.example.ecolums;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an application user — either a Student, Club Leader, or Admin.
 *
 * <p><b>Responsibilities (from CRC card):</b></p>
 * <ul>
 *   <li>Maintain personal profile, role, and club membership.</li>
 *   <li>Submit, edit, and delete personal sustainability activity logs.</li>
 *   <li>View personal sustainability progress and dashboard metrics.</li>
 *   <li>Participate in challenges and track badges or leaderboard ranking.</li>
 *   <li>Manage participation in clubs, team challenges, and invitation-based membership.</li>
 * </ul>
 *
 * <p><b>Collaborators:</b> {@link ProgressTracker}, {@link AdminDashboard},
 * {@link Leaderboard}, {@link ActivityLog}.</p>
 *
 * <p>Stored in the Firestore collection {@code users}.</p>
 * <p>
 * Outstanding issues: Firebase Authentication UID must always equal userId
 * — this invariant is enforced at sign-up but should be validated on load.
 */
public class User {

	// -------------------------------------------------------------------------
	// Constants — roles
	// -------------------------------------------------------------------------

	public static final String ROLE_STUDENT = "STUDENT";
	public static final String ROLE_CLUB_LEADER = "CLUB_LEADER";
	public static final String ROLE_ADMIN = "ADMIN";

	// -------------------------------------------------------------------------
	// Fields
	// -------------------------------------------------------------------------

	/**
	 * Firebase Auth UID, also the Firestore document ID in the {@code users} collection.
	 */
	private String userId;

	/**
	 * Student's display name.
	 */
	private String name;

	/**
	 * University email address (used for login).
	 */
	private String email;

	/**
	 * One of {@link #ROLE_STUDENT}, {@link #ROLE_CLUB_LEADER}, or {@link #ROLE_ADMIN}.
	 */
	private String role;

	/**
	 * ID of the club this user belongs to. Null if not in any club.
	 */
	private String clubId;

	/**
	 * Total accumulated green points across all activity logs.
	 */
	private double totalPoints;

	/**
	 * List of badge IDs that have been earned.
	 * Full {@link Badge} objects are stored in the sub-collection {@code users/{userId}/badges}.
	 */
	private List<String> earnedBadgeIds;

	/**
	 * URL to the user's profile avatar in Firebase Storage.
	 */
	private String profileImageUrl;

	/**
	 * Whether the user has opted in to sharing data with campus clubs.
	 */
	private boolean shareDataWithClubs;

	/**
	 * When the user account was created.
	 */
	private Timestamp createdAt;

	/**
	 * Current consecutive daily activity streak (days in a row).
	 */
	private int currentStreak;

	/**
	 * Timestamp of the last activity logged — used to compute streak continuity.
	 */
	private Timestamp lastActivityDate;

	/**
	 * Firestore instance (transient — not persisted).
	 */
	private transient FirebaseFirestore db;

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Required no-arg constructor for Firestore deserialization.
	 */
	public User() {
		this.db = FirebaseFirestore.getInstance();
	}

	/**
	 * Creates a new User.
	 *
	 * @param userId Firebase Auth UID.
	 * @param name   Display name.
	 * @param email  University email.
	 * @param role   One of the ROLE_* constants.
	 */
	public User(String userId, String name, String email, String role) {
		this.userId = userId;
		this.name = name;
		this.email = email;
		this.role = role;
		this.totalPoints = 0.0;
		this.earnedBadgeIds = new ArrayList<>();
		this.shareDataWithClubs = false;
		this.createdAt = Timestamp.now();
		this.db = FirebaseFirestore.getInstance();
	}

	// -------------------------------------------------------------------------
	// Profile management
	// -------------------------------------------------------------------------

	/**
	 * Persists this user's profile fields (name, email, avatarUrl) to Firestore.
	 *
	 * <p>Only the mutable profile fields are written; role and userId are immutable
	 * after account creation.</p>
	 *
	 * @param callback Invoked with {@code true} on success, {@code false} on failure.
	 */
	public void updateProfile(OnCompleteCallback callback) {
		Map<String, Object> updates = new HashMap<>();
		updates.put("name", name);
		updates.put("email", email);
		updates.put("profileImageUrl", profileImageUrl);
		updates.put("shareDataWithClubs", shareDataWithClubs);

		db.collection("users").document(userId)
				.update(updates)
				.addOnSuccessListener(aVoid -> callback.onComplete(true))
				.addOnFailureListener(e -> callback.onComplete(false));
	}

	/**
	 * Loads this user's profile from Firestore and populates the object's fields.
	 *
	 * @param targetUserId The UID of the user to load.
	 * @param callback     Invoked with {@code true} on success, {@code false} on failure.
	 */
	public void loadProfile(String targetUserId, OnCompleteCallback callback) {
		db.collection("users").document(targetUserId)
				.get()
				.addOnSuccessListener(documentSnapshot -> {
					if (documentSnapshot.exists()) {
						User loaded = documentSnapshot.toObject(User.class);
						if (loaded != null) {
							this.userId = loaded.userId;
							this.name = loaded.name;
							this.email = loaded.email;
							this.role = loaded.role;
							this.clubId = loaded.clubId;
							this.totalPoints = loaded.totalPoints;
							this.earnedBadgeIds = loaded.earnedBadgeIds;
							this.profileImageUrl = loaded.profileImageUrl;
							this.shareDataWithClubs = loaded.shareDataWithClubs;
							this.createdAt = loaded.createdAt;
							this.currentStreak = loaded.currentStreak;
							this.lastActivityDate = loaded.lastActivityDate;
						}
						callback.onComplete(true);
					} else {
						callback.onComplete(false);
					}
				})
				.addOnFailureListener(e -> callback.onComplete(false));
	}

	// -------------------------------------------------------------------------
	// Club membership
	// -------------------------------------------------------------------------

	/**
	 * Enrolls this user in a club using a valid join code or invite link.
	 *
	 * <p>Looks up the club with the provided code in Firestore, validates that
	 * the code is active and not expired, then updates both the user document
	 * (setting {@code clubId}) and the club document (adding this userId to
	 * {@code memberIds}).</p>
	 *
	 * @param joinCode The alphanumeric code entered by the user.
	 * @param callback Invoked with {@code true} if enrollment succeeded.
	 */
	public void joinClubWithCode(String joinCode, OnCompleteCallback callback) {
		if (db == null) db = FirebaseFirestore.getInstance();
		if (this.clubId != null) {
			callback.onComplete(false); // already in a club
			return;
		}
		db.collection("clubs")
				.whereEqualTo("joinCode", joinCode)
				.whereEqualTo("joinCodeActive", true)
				.get()
				.addOnSuccessListener(querySnapshot -> {
					if (querySnapshot.isEmpty()) {
						callback.onComplete(false);
						return;
					}
					var doc = querySnapshot.getDocuments().get(0);
					Club club = doc.toObject(Club.class);
					if (club == null) {
						callback.onComplete(false);
						return;
					}
					// Check expiry
					Timestamp expiry = club.getJoinCodeExpiry();
					if (expiry != null && expiry.toDate().before(new java.util.Date())) {
						callback.onComplete(false);
						return;
					}
					String foundClubId = doc.getId();
					com.google.firebase.firestore.WriteBatch batch = db.batch();
					DocumentReference clubRef = db.collection("clubs").document(foundClubId);
					DocumentReference userRef = db.collection("users").document(this.userId);
					batch.update(clubRef, "memberIds", FieldValue.arrayUnion(this.userId));
					batch.update(userRef, "clubId", foundClubId);
					batch.commit()
							.addOnSuccessListener(aVoid -> {
								this.clubId = foundClubId;
								callback.onComplete(true);
							})
							.addOnFailureListener(e -> callback.onComplete(false));
				})
				.addOnFailureListener(e -> callback.onComplete(false));
	}

	/**
	 * Removes this user from their current club.
	 *
	 * <p>Clears {@code clubId} on the user document and removes this userId
	 * from the club's {@code memberIds} array.</p>
	 *
	 * @param callback Invoked with {@code true} on success.
	 */
	public void leaveClub(OnCompleteCallback callback) {
		if (clubId == null) {
			callback.onComplete(false);
			return;
		}
		if (db == null) db = FirebaseFirestore.getInstance();
		String currentClubId = this.clubId;
		com.google.firebase.firestore.WriteBatch batch = db.batch();
		DocumentReference clubRef = db.collection("clubs").document(currentClubId);
		DocumentReference userRef = db.collection("users").document(userId);
		batch.update(clubRef, "memberIds", FieldValue.arrayRemove(userId));
		batch.update(userRef, "clubId", null);
		batch.commit()
				.addOnSuccessListener(aVoid -> {
					this.clubId = null;
					callback.onComplete(true);
				})
				.addOnFailureListener(e -> callback.onComplete(false));
	}

	// -------------------------------------------------------------------------
	// Points and badges
	// -------------------------------------------------------------------------

	/**
	 * Adds the specified number of green points to this user's total and
	 * persists the updated total to Firestore.
	 *
	 * <p>After updating points, calls {@link ProgressTracker#checkMilestonesForUser}
	 * to determine whether any badges should be awarded.</p>
	 *
	 * @param pointsToAdd Number of points earned from a new activity log.
	 * @param callback    Invoked with {@code true} on success.
	 */
	public void addPoints(double pointsToAdd, OnCompleteCallback callback) {
		this.totalPoints += pointsToAdd;

		db.collection("users").document(userId)
				.update("totalPoints", this.totalPoints)
				.addOnSuccessListener(aVoid -> {
					new ProgressTracker().checkMilestonesForUser(this, ignored -> {});
					callback.onComplete(true);
				})
				.addOnFailureListener(e -> callback.onComplete(false));
	}

	/**
	 * Marks a badge as earned for this user, storing it in the
	 * {@code users/{userId}/badges} sub-collection and updating
	 * {@link #earnedBadgeIds}.
	 *
	 * @param badge    The badge to award.
	 * @param callback Invoked with {@code true} on success.
	 */
	public void awardBadge(Badge badge, OnCompleteCallback callback) {
		if (earnedBadgeIds == null) earnedBadgeIds = new ArrayList<>();
		if (earnedBadgeIds.contains(badge.getBadgeId())) {
			callback.onComplete(true); // already earned
			return;
		}

		badge.setStatus(Badge.STATUS_EARNED);
		badge.setDateEarned(Timestamp.now());

		DocumentReference badgeRef = db.collection("users")
				.document(userId)
				.collection("badges")
				.document(badge.getBadgeId());

		Map<String, Object> badgeData = new HashMap<>();
		badgeData.put("badgeId", badge.getBadgeId());
		badgeData.put("name", badge.getName());
		badgeData.put("description", badge.getDescription());
		badgeData.put("iconUrl", badge.getIconUrl());
		badgeData.put("badgeType", badge.getBadgeType());
		badgeData.put("badgeTier", badge.getBadgeTier());
		badgeData.put("status", Badge.STATUS_EARNED);
		badgeData.put("dateEarned", badge.getDateEarned());

		com.google.firebase.firestore.WriteBatch batch = db.batch();
		batch.set(badgeRef, badgeData);
		batch.update(
				db.collection("users").document(userId),
				"earnedBadgeIds", FieldValue.arrayUnion(badge.getBadgeId())
		);
		earnedBadgeIds.add(badge.getBadgeId());
		batch.commit()
				.addOnSuccessListener(v -> callback.onComplete(true))
				.addOnFailureListener(e -> callback.onComplete(false));
	}

	// -------------------------------------------------------------------------
	// Challenge participation
	// -------------------------------------------------------------------------

	/**
	 * Enrolls this user in a specific green challenge.
	 *
	 * <p>Adds userId to the challenge's {@code participantIds} array in Firestore
	 * and initialises their score entry in {@code participantScores} to 0.</p>
	 *
	 * @param challengeId The Firestore document ID of the challenge to join.
	 * @param callback    Invoked with {@code true} on success.
	 */
	public void joinChallenge(String challengeId, OnCompleteCallback callback) {
		FirebaseFirestore db = FirebaseFirestore.getInstance();
		Map<String, Object> updates = new HashMap<>();
		updates.put("participantIds", FieldValue.arrayUnion(userId));
		updates.put("participantScores." + userId, 0.0);
		db.collection("challenges").document(challengeId)
				.update(updates)
				.addOnSuccessListener(aVoid -> callback.onComplete(true))
				.addOnFailureListener(e -> callback.onComplete(false));
	}

	// -------------------------------------------------------------------------
	// Callback interface
	// -------------------------------------------------------------------------

	/**
	 * Simple callback for async Firestore operations.
	 */
	public interface OnCompleteCallback {
		/**
		 * @param success {@code true} if the operation completed without error.
		 */
		void onComplete(boolean success);
	}

	// -------------------------------------------------------------------------
	// Getters and Setters
	// -------------------------------------------------------------------------

	public String getUserId() {
		return userId;
	}

	public void setUserId(String v) {
		userId = v;
	}

	public String getName() {
		return name;
	}

	public void setName(String v) {
		name = v;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String v) {
		email = v;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String v) {
		role = v;
	}

	public String getClubId() {
		return clubId;
	}

	public void setClubId(String v) {
		clubId = v;
	}

	public double getTotalPoints() {
		return totalPoints;
	}

	public void setTotalPoints(double v) {
		totalPoints = v;
	}

	public List<String> getEarnedBadgeIds() {
		return earnedBadgeIds;
	}

	public void setEarnedBadgeIds(List<String> v) {
		earnedBadgeIds = v;
	}

	public String getProfileImageUrl() {
		return profileImageUrl;
	}

	public void setProfileImageUrl(String v) {
		profileImageUrl = v;
	}

	public boolean isShareDataWithClubs() {
		return shareDataWithClubs;
	}

	public void setShareDataWithClubs(boolean v) {
		shareDataWithClubs = v;
	}

	public Timestamp getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Timestamp v) {
		createdAt = v;
	}

	public int getCurrentStreak() {
		return currentStreak;
	}

	public void setCurrentStreak(int v) {
		currentStreak = v;
	}

	public Timestamp getLastActivityDate() {
		return lastActivityDate;
	}

	public void setLastActivityDate(Timestamp v) {
		lastActivityDate = v;
	}
}
