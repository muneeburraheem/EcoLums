package com.example.ecolums;

import com.google.firebase.Timestamp;

import java.util.List;

/**
 * Represents a campus-wide sustainability milestone goal set by the
 * Campus Sustainability Office (CSO).
 *
 * <p>Stored in the Firestore collection {@code campusGoals}. The
 * {@link AdminDashboard} creates and archives goals; {@link ProgressTracker}
 * reads them to compute current progress and fire milestone notifications.</p>
 * <p>
 * Outstanding issues: "Top contributors" field for the archive view is not yet wired up.
 */
public class CampusGoal {

	// -------------------------------------------------------------------------
	// Constants — goal status
	// -------------------------------------------------------------------------

	public static final String STATUS_ACTIVE = "ACTIVE";
	public static final String STATUS_COMPLETED = "COMPLETED";
	public static final String STATUS_ARCHIVED = "ARCHIVED";

	// -------------------------------------------------------------------------
	// Constants — metric type
	// -------------------------------------------------------------------------

	public static final String METRIC_CO2 = "CO2";     // unit: lbs
	public static final String METRIC_WASTE = "WASTE";   // unit: lbs
	public static final String METRIC_ENERGY = "ENERGY";  // unit: kWh

	// -------------------------------------------------------------------------
	// Fields
	// -------------------------------------------------------------------------

	/**
	 * Firestore document ID.
	 */
	private String goalId;

	/**
	 * Human-readable title, e.g. "Save 10,000 lbs of CO2 this semester".
	 */
	private String title;

	/**
	 * The sustainability metric this goal tracks (see METRIC_* constants).
	 */
	private String metricType;

	/**
	 * Numeric target value to reach (e.g. 10000.0 lbs).
	 */
	private double targetValue;

	/**
	 * Auto-aggregated current progress pulled from all user logs.
	 */
	private double currentProgress;

	/**
	 * When the goal period begins.
	 */
	private Timestamp startDate;

	/**
	 * When the goal period ends.
	 */
	private Timestamp endDate;

	/**
	 * One of {@link #STATUS_ACTIVE}, {@link #STATUS_COMPLETED}, {@link #STATUS_ARCHIVED}.
	 */
	private String status;

	/**
	 * Percentage milestones at which campus-wide notifications should fire
	 * (e.g. [25, 50, 75, 100]).
	 */
	private List<Integer> notificationMilestones;

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Required no-arg constructor for Firestore deserialization.
	 */
	public CampusGoal() {
	}

	/**
	 * Creates a new CampusGoal.
	 *
	 * @param goalId                 Firestore document ID.
	 * @param title                  Display title.
	 * @param metricType             One of the METRIC_* constants.
	 * @param targetValue            Numeric target.
	 * @param startDate              Goal period start.
	 * @param endDate                Goal period end.
	 * @param notificationMilestones Percentage checkpoints for notifications.
	 */
	public CampusGoal(
			String goalId, String title, String metricType,
			double targetValue, Timestamp startDate,
			Timestamp endDate, List<Integer> notificationMilestones
	) {
		this.goalId = goalId;
		this.title = title;
		this.metricType = metricType;
		this.targetValue = targetValue;
		this.currentProgress = 0.0;
		this.startDate = startDate;
		this.endDate = endDate;
		this.status = STATUS_ACTIVE;
		this.notificationMilestones = notificationMilestones;
	}

	// -------------------------------------------------------------------------
	// Derived helpers
	// -------------------------------------------------------------------------

	/**
	 * Returns the percentage of the target that has been completed.
	 *
	 * @return value between 0.0 and 100.0.
	 */
	public double getProgressPercentage() {
		if (targetValue == 0) return 0.0;
		return (currentProgress / targetValue) * 100.0;
	}

	/**
	 * Returns how much of the target still remains.
	 *
	 * @return remaining units (same unit as targetValue).
	 */
	public double getRemainingValue() {
		return Math.max(0.0, targetValue - currentProgress);
	}

	// -------------------------------------------------------------------------
	// Getters and Setters
	// -------------------------------------------------------------------------

	public String getGoalId() {
		return goalId;
	}

	public void setGoalId(String v) {
		goalId = v;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String v) {
		title = v;
	}

	public String getMetricType() {
		return metricType;
	}

	public void setMetricType(String v) {
		metricType = v;
	}

	public double getTargetValue() {
		return targetValue;
	}

	public void setTargetValue(double v) {
		targetValue = v;
	}

	public double getCurrentProgress() {
		return currentProgress;
	}

	public void setCurrentProgress(double v) {
		currentProgress = v;
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

	public String getStatus() {
		return status;
	}

	public void setStatus(String v) {
		status = v;
	}

	public List<Integer> getNotificationMilestones() {
		return notificationMilestones;
	}

	public void setNotificationMilestones(List<Integer> v) {
		notificationMilestones = v;
	}
}
