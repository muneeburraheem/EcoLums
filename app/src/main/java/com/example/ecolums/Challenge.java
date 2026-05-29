package com.example.ecolums;

import com.google.firebase.Timestamp;

import java.util.List;
import java.util.Map;

/**
 * Represents a green competition or challenge that students and clubs can
 * participate in to earn points and badges.
 *
 * <p>Stored in the Firestore collection {@code challenges}. Challenges are
 * created by club leaders or admins via {@link AdminDashboard}. Individual
 * user scores are aggregated through {@link ProgressTracker} and displayed
 * on the per-challenge leaderboard managed by {@link Leaderboard}.</p>
 * <p>
 * Outstanding issues: Per-capita averaging logic for inter-team fairness
 * is not yet implemented.
 */
public class Challenge {

	// -------------------------------------------------------------------------
	// Constants — status
	// -------------------------------------------------------------------------

	public static final String STATUS_UPCOMING = "UPCOMING";
	public static final String STATUS_ACTIVE = "ACTIVE";
	public static final String STATUS_COMPLETED = "COMPLETED";

	// -------------------------------------------------------------------------
	// Constants — goal type (mirrors ActivityLog categories)
	// -------------------------------------------------------------------------

	public static final String GOAL_TYPE_TRANSPORT = "TRANSPORT";
	public static final String GOAL_TYPE_ENERGY = "ENERGY";
	public static final String GOAL_TYPE_WASTE = "WASTE";
	public static final String GOAL_TYPE_OVERALL = "OVERALL";

	// -------------------------------------------------------------------------
	// Fields
	// -------------------------------------------------------------------------

	/**
	 * Firestore document ID.
	 */
	private String challengeId;

	/**
	 * Human-readable title (e.g. "Plastic-Free February").
	 */
	private String title;

	/**
	 * Short description of the challenge shown in the public gallery.
	 */
	private String description;

	/**
	 * UID or club ID of the user who created this challenge.
	 */
	private String creatorId;

	/**
	 * One of {@link #STATUS_UPCOMING}, {@link #STATUS_ACTIVE}, {@link #STATUS_COMPLETED}.
	 */
	private String status;

	/**
	 * One of the GOAL_TYPE_* constants — which log category counts toward this challenge.
	 */
	private String goalType;

	/**
	 * Numeric target participants must collectively or individually reach.
	 */
	private double targetValue;

	/**
	 * Human-readable unit for the target (e.g. "km", "kWh", "kg").
	 */
	private String targetUnit;

	/**
	 * When the challenge starts.
	 */
	private Timestamp startDate;

	/**
	 * When the challenge ends.
	 */
	private Timestamp endDate;

	/**
	 * List of user UIDs or club IDs enrolled in this challenge.
	 * Populated when users tap "Join".
	 */
	private List<String> participantIds;

	/**
	 * Map of participantId → their current score toward the challenge goal.
	 * Updated automatically as users log activities during the challenge period.
	 */
	private Map<String, Double> participantScores;

	/**
	 * Badge ID awarded to qualifying participants when the challenge concludes.
	 * Null if no badge is tied to this challenge.
	 */
	private String rewardBadgeId;

	/**
	 * Percentage milestones (e.g. [50, 100]) at which automated in-app
	 * notifications are sent to participants.
	 */
	private List<Integer> notificationMilestones;

	/**
	 * AC 1 (US_03.03): 6-character alphanumeric invite code for this challenge.
	 */
	private String inviteCode;

	/**
	 * AC 3 (US_03.03): True when the club leader manually deactivates the invite link.
	 */
	private boolean isManualDeactivated;

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Required no-arg constructor for Firestore deserialization.
	 */
	public Challenge() {
	}

	/**
	 * Creates a new Challenge.
	 *
	 * @param challengeId            Firestore document ID.
	 * @param title                  Display title.
	 * @param description            Short description for the gallery.
	 * @param creatorId              UID or club ID of the creator.
	 * @param goalType               One of the GOAL_TYPE_* constants.
	 * @param targetValue            Numeric target.
	 * @param targetUnit             Unit for the target.
	 * @param startDate              When the challenge begins.
	 * @param endDate                When the challenge ends.
	 * @param notificationMilestones Percentage thresholds for notifications.
	 */
	public Challenge(
			String challengeId, String title, String description,
			String creatorId, String goalType, double targetValue,
			String targetUnit, Timestamp startDate, Timestamp endDate,
			List<Integer> notificationMilestones
	) {
		this.challengeId = challengeId;
		this.title = title;
		this.description = description;
		this.creatorId = creatorId;
		this.goalType = goalType;
		this.targetValue = targetValue;
		this.targetUnit = targetUnit;
		this.startDate = startDate;
		this.endDate = endDate;
		this.notificationMilestones = notificationMilestones;
		this.status = STATUS_UPCOMING;
	}

	// -------------------------------------------------------------------------
	// Getters and Setters
	// -------------------------------------------------------------------------

	public String getChallengeId() {
		return challengeId;
	}

	public void setChallengeId(String v) {
		challengeId = v;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String v) {
		title = v;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String v) {
		description = v;
	}

	public String getCreatorId() {
		return creatorId;
	}

	public void setCreatorId(String v) {
		creatorId = v;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String v) {
		status = v;
	}

	public String getGoalType() {
		return goalType;
	}

	public void setGoalType(String v) {
		goalType = v;
	}

	public double getTargetValue() {
		return targetValue;
	}

	public void setTargetValue(double v) {
		targetValue = v;
	}

	public String getTargetUnit() {
		return targetUnit;
	}

	public void setTargetUnit(String v) {
		targetUnit = v;
	}

	public Timestamp getStartDate() {
		return startDate;
	}

	public void setStartDate(Timestamp v) {
		startDate = v;
	}

	public Timestamp getEndDate() {
		return endDate;
	}

	public void setEndDate(Timestamp v) {
		endDate = v;
	}

	public List<String> getParticipantIds() {
		return participantIds;
	}

	public void setParticipantIds(List<String> v) {
		participantIds = v;
	}

	public Map<String, Double> getParticipantScores() {
		return participantScores;
	}

	public void setParticipantScores(Map<String, Double> v) {
		participantScores = v;
	}

	public String getRewardBadgeId() {
		return rewardBadgeId;
	}

	public void setRewardBadgeId(String v) {
		rewardBadgeId = v;
	}

	public List<Integer> getNotificationMilestones() {
		return notificationMilestones;
	}

	public void setNotificationMilestones(List<Integer> v) {
		notificationMilestones = v;
	}

	public String getInviteCode() {
		return inviteCode;
	}

	public void setInviteCode(String v) {
		inviteCode = v;
	}

	public boolean isManualDeactivated() {
		return isManualDeactivated;
	}

	public void setManualDeactivated(boolean v) {
		isManualDeactivated = v;
	}
}
