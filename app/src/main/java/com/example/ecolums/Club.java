package com.example.ecolums;

import com.google.firebase.Timestamp;

import java.util.List;

/**
 * Represents a campus club or department that can participate in team-based
 * sustainability challenges.
 *
 * <p>Stored in the Firestore collection {@code clubs}. Clubs are created by
 * users with the "Club Leader" role. {@link User} objects reference a clubId
 * when they join; {@link ProgressTracker} aggregates member logs into the
 * club's collective score; {@link Leaderboard} ranks clubs in team challenges.</p>
 * <p>
 * Outstanding issues: Invite-link expiration logic needs a Cloud Function
 * to auto-deactivate expired codes server-side.
 */
public class Club {

	// -------------------------------------------------------------------------
	// Fields
	// -------------------------------------------------------------------------

	/**
	 * Firestore document ID.
	 */
	private String clubId;

	/**
	 * Human-readable club name (must be unique).
	 */
	private String name;

	/**
	 * Short description of the club's sustainability focus.
	 */
	private String description;

	/**
	 * URL to the club avatar image stored in Firebase Storage.
	 */
	private String avatarUrl;

	/**
	 * UID of the user who created and leads this club.
	 */
	private String leaderId;

	/**
	 * List of UIDs of all current club members (including the leader).
	 */
	private List<String> memberIds;

	/**
	 * Alphanumeric join code students can enter to be automatically enrolled.
	 * Null if no active code has been generated.
	 */
	private String joinCode;

	/**
	 * Full shareable invite URL. When navigated to in-app, it routes
	 * directly to this club's joining page.
	 */
	private String inviteLink;

	/**
	 * When the current join code / invite link expires. Null if no expiry set.
	 */
	private Timestamp joinCodeExpiry;

	/**
	 * Whether the join code / invite link is currently active.
	 */
	private boolean joinCodeActive;

	/**
	 * Aggregate total green points earned by all members combined.
	 */
	private double totalPoints;

	/**
	 * List of challenge IDs this club is currently enrolled in.
	 */
	private List<String> activeChallengeIds;

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Required no-arg constructor for Firestore deserialization.
	 */
	public Club() {
	}

	/**
	 * Creates a new Club.
	 *
	 * @param clubId      Firestore document ID.
	 * @param name        Unique club name.
	 * @param description Short description.
	 * @param leaderId    UID of the founding leader.
	 */
	public Club(String clubId, String name, String description, String leaderId) {
		this.clubId = clubId;
		this.name = name;
		this.description = description;
		this.leaderId = leaderId;
		this.totalPoints = 0.0;
		this.joinCodeActive = false;
	}

	// -------------------------------------------------------------------------
	// Getters and Setters
	// -------------------------------------------------------------------------

	public String getClubId() {
		return clubId;
	}

	public void setClubId(String v) {
		clubId = v;
	}

	public String getName() {
		return name;
	}

	public void setName(String v) {
		name = v;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String v) {
		description = v;
	}

	public String getAvatarUrl() {
		return avatarUrl;
	}

	public void setAvatarUrl(String v) {
		avatarUrl = v;
	}

	public String getLeaderId() {
		return leaderId;
	}

	public void setLeaderId(String v) {
		leaderId = v;
	}

	public List<String> getMemberIds() {
		return memberIds;
	}

	public void setMemberIds(List<String> v) {
		memberIds = v;
	}

	public String getJoinCode() {
		return joinCode;
	}

	public void setJoinCode(String v) {
		joinCode = v;
	}

	public String getInviteLink() {
		return inviteLink;
	}

	public void setInviteLink(String v) {
		inviteLink = v;
	}

	public Timestamp getJoinCodeExpiry() {
		return joinCodeExpiry;
	}

	public void setJoinCodeExpiry(Timestamp v) {
		joinCodeExpiry = v;
	}

	public boolean isJoinCodeActive() {
		return joinCodeActive;
	}

	public void setJoinCodeActive(boolean v) {
		joinCodeActive = v;
	}

	public double getTotalPoints() {
		return totalPoints;
	}

	public void setTotalPoints(double v) {
		totalPoints = v;
	}

	public List<String> getActiveChallengeIds() {
		return activeChallengeIds;
	}

	public void setActiveChallengeIds(List<String> v) {
		activeChallengeIds = v;
	}
}
