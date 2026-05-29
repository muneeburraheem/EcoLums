package com.example.ecolums;

import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Aggregates individual user activity data into personal and campus-wide
 * sustainability metrics, evaluates milestone thresholds, and triggers
 * celebratory notifications when goals are reached.
 *
 * <p><b>Responsibilities (from CRC card):</b></p>
 * <ul>
 *   <li>Compare individual stats against milestones.</li>
 *   <li>Aggregate all student data for campus-wide impact view.</li>
 *   <li>Apply calculation metrics to totals.</li>
 *   <li>Check if a goal is achieved and send a celebratory sticker.</li>
 *   <li>Provide aggregated sustainability analytics for users, clubs, and campus.</li>
 * </ul>
 *
 * <p><b>Collaborators:</b> {@link User}, {@link ActivityLog},
 * {@link AdminDashboard}, {@link Leaderboard}.</p>
 *
 * <p>Reads from the {@code users}, {@code activityLogs}, {@code campusGoals},
 * and {@code calculationMetrics} Firestore collections. Writes celebratory
 * notifications to {@code notifications/{userId}}.</p>
 * <p>
 * Outstanding issues: Campus-wide aggregation currently reads all user docs,
 * which will not scale. Replace with a Cloud Function that maintains a
 * running total in a dedicated {@code campusStats} document.
 */
public class ProgressTracker {

	// -------------------------------------------------------------------------
	// Fields
	// -------------------------------------------------------------------------

	/**
	 * Firestore instance.
	 */
	private final FirebaseFirestore db;

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Creates a new ProgressTracker backed by Firestore.
	 */
	public ProgressTracker() {
		this.db = FirebaseFirestore.getInstance();
	}

	// -------------------------------------------------------------------------
	// Personal progress
	// -------------------------------------------------------------------------

	/**
	 * Loads the personal impact summary for a user over a given time range.
	 *
	 * <p>Queries {@code users/{userId}/activityLogs} for entries within
	 * [{@code from}, {@code to}] and sums CO2 saved, waste diverted,
	 * and energy saved into an {@link ImpactSummary} object.</p>
	 *
	 * @param userId   UID of the target user.
	 * @param from     Start of the time range.
	 * @param to       End of the time range.
	 * @param callback Receives the computed {@link ImpactSummary}.
	 */
	public void getPersonalImpactSummary(
			String userId, Timestamp from, Timestamp to,
			OnSummaryLoadedCallback callback
	) {
		db.collection("users")
				.document(userId)
				.collection("activityLogs")
				.whereGreaterThanOrEqualTo("date", from)
				.whereLessThanOrEqualTo("date", to)
				.get()
				.addOnSuccessListener(querySnapshot -> {
					ImpactSummary summary = new ImpactSummary();

					for (var doc : querySnapshot.getDocuments()) {
						ActivityLog log = doc.toObject(ActivityLog.class);
						if (log == null) continue;

						switch (log.getCategory()) {
						case ActivityLog.CATEGORY_TRANSPORT:
						case ActivityLog.CATEGORY_ENERGY:
							summary.totalCO2SavedKg += log.getCo2EquivalentKg();
							summary.totalEnergySavedKwh += log.getEnergyKwh();
							break;
						case ActivityLog.CATEGORY_WASTE:
							summary.totalWasteDivertedKg += log.getWasteKg();
							summary.totalCO2SavedKg += log.getCo2EquivalentKg();
							break;
						}
						summary.totalPointsEarned += log.getPointsEarned();
					}

					callback.onSummaryLoaded(summary);
				})
				.addOnFailureListener(e -> {
					Log.e("ProgressTracker", "Failed to load personal impact summary for " + userId, e);
					callback.onSummaryLoaded(null);
				});
	}

	/**
	 * Retrieves time-series data for a user's CO2 savings, suitable for
	 * rendering a line chart on the personal dashboard.
	 *
	 * <p>Groups log entries by day (or week/month depending on {@code granularity})
	 * and returns an ordered list of {@link DataPoint} objects.</p>
	 *
	 * @param userId      UID of the target user.
	 * @param from        Start of the time range.
	 * @param to          End of the time range.
	 * @param granularity One of {@code "daily"}, {@code "weekly"}, {@code "monthly"}.
	 * @param callback    Receives the ordered list of data points.
	 */
	public void getPersonalTimeSeries(
			String userId, Timestamp from, Timestamp to,
			String granularity, OnTimeSeriesLoadedCallback callback
	) {
		db.collection("users")
				.document(userId)
				.collection("activityLogs")
				.whereGreaterThanOrEqualTo("date", from)
				.whereLessThanOrEqualTo("date", to)
				.get()
				.addOnSuccessListener(querySnapshot -> {
					Map<String, Double> buckets = new TreeMap<>();
					for (var doc : querySnapshot.getDocuments()) {
						Timestamp ts = doc.getTimestamp("date");
						Double co2 = doc.getDouble("co2EquivalentKg");
						if (ts == null || co2 == null) continue;
						String key = bucketLabel(ts, granularity);
						buckets.put(key, buckets.getOrDefault(key, 0.0) + co2);
					}
					callback.onTimeSeriesLoaded(toDataPoints(buckets));
				})
				.addOnFailureListener(e -> callback.onTimeSeriesLoaded(new ArrayList<>()));
	}

	// -------------------------------------------------------------------------
	// Milestone and badge checking
	// -------------------------------------------------------------------------

	/**
	 * Checks whether a user has crossed any badge point thresholds after
	 * accumulating new points, and awards any newly earned badges.
	 *
	 * <p>Reads all badge definitions from {@code badges} collection, filters
	 * to those the user has not yet earned, then compares their
	 * {@code pointThreshold} against the user's {@code totalPoints}.
	 * Awards matching badges via {@link User#awardBadge}.</p>
	 *
	 * @param user     The user to evaluate; must have a valid {@code totalPoints} value.
	 * @param callback Invoked with a list of newly awarded badges (may be empty).
	 */
	public void checkMilestonesForUser(User user, OnMilestonesCheckedCallback callback) {
		db.collection("badges")
				.get()
				.addOnSuccessListener(querySnapshot -> {
					List<Badge> newlyEarned = new ArrayList<>();
					List<String> alreadyEarned = user.getEarnedBadgeIds() != null
							? user.getEarnedBadgeIds()
							: new ArrayList<>();

					for (var doc : querySnapshot.getDocuments()) {
						Badge badge = doc.toObject(Badge.class);
						if (badge == null) continue;
						if (alreadyEarned.contains(badge.getBadgeId())) continue;

						String type = badge.getBadgeType();
						boolean qualifies = false;

						if (Badge.TYPE_POINT_THRESHOLD.equals(type) || Badge.TYPE_HARDCODED.equals(type)) {
							qualifies = badge.getPointThreshold() > 0
									&& user.getTotalPoints() >= badge.getPointThreshold();
						} else if (Badge.TYPE_STREAK.equals(type)) {
							qualifies = badge.getStreakRequired() > 0
									&& user.getCurrentStreak() >= badge.getStreakRequired();
						}
						// ACTIVITY_COUNT, CHALLENGE_*, and other HARDCODED types are checked
						// at their specific trigger points (not here).

						if (qualifies) newlyEarned.add(badge);
					}

					for (Badge badge : newlyEarned) {
						user.awardBadge(badge, success -> {
							if (success) sendMilestoneNotification(user.getUserId(), badge);
						});
					}

					callback.onMilestonesChecked(newlyEarned);
				})
				.addOnFailureListener(e -> {
					Log.e("ProgressTracker", "Failed to load badge definitions for milestone check", e);
					callback.onMilestonesChecked(new ArrayList<>());
				});
	}

	/** Awards streak-based badges when the user's streak is updated. */
	public void checkStreakBadges(User user, int currentStreak) {
		db.collection("badges")
				.whereEqualTo("badgeType", Badge.TYPE_STREAK)
				.get()
				.addOnSuccessListener(querySnapshot -> {
					List<String> alreadyEarned = user.getEarnedBadgeIds() != null
							? user.getEarnedBadgeIds() : new ArrayList<>();
					for (var doc : querySnapshot.getDocuments()) {
						Badge badge = doc.toObject(Badge.class);
						if (badge == null || alreadyEarned.contains(badge.getBadgeId())) continue;
						if (badge.getStreakRequired() > 0 && currentStreak >= badge.getStreakRequired()) {
							user.awardBadge(badge, success -> {
								if (success) sendMilestoneNotification(user.getUserId(), badge);
							});
						}
					}
				});
	}

	/** Checks and awards the "first activity" hardcoded badge. */
	public void checkFirstActivityBadge(User user) {
		List<String> earned = user.getEarnedBadgeIds();
		if (earned != null && earned.contains(BadgeDefinitions.ID_FIRST_STEP)) return;

		db.collection("badges").document(BadgeDefinitions.ID_FIRST_STEP)
				.get()
				.addOnSuccessListener(doc -> {
					if (!doc.exists()) return;
					Badge badge = doc.toObject(Badge.class);
					if (badge == null) return;
					user.awardBadge(badge, success -> {
						if (success) sendMilestoneNotification(user.getUserId(), badge);
					});
				});
	}

	/** Checks and awards the "join a club" hardcoded badge. */
	public void checkClubJoinBadge(User user) {
		List<String> earned = user.getEarnedBadgeIds();
		if (earned != null && earned.contains(BadgeDefinitions.ID_CLUB_MEMBER)) return;

		db.collection("badges").document(BadgeDefinitions.ID_CLUB_MEMBER)
				.get()
				.addOnSuccessListener(doc -> {
					if (!doc.exists()) return;
					Badge badge = doc.toObject(Badge.class);
					if (badge == null) return;
					user.awardBadge(badge, success -> {
						if (success) sendMilestoneNotification(user.getUserId(), badge);
					});
				});
	}

	/** Checks and awards the "join a challenge" hardcoded badge. */
	public void checkChallengeJoinBadge(User user) {
		List<String> earned = user.getEarnedBadgeIds();
		if (earned != null && earned.contains(BadgeDefinitions.ID_CHALLENGE_JOINER)) return;

		db.collection("badges").document(BadgeDefinitions.ID_CHALLENGE_JOINER)
				.get()
				.addOnSuccessListener(doc -> {
					if (!doc.exists()) return;
					Badge badge = doc.toObject(Badge.class);
					if (badge == null) return;
					user.awardBadge(badge, success -> {
						if (success) sendMilestoneNotification(user.getUserId(), badge);
					});
				});
	}

	/**
	 * Checks activity-count badges based on transport or waste mode.
	 * Counts how many times the user has logged the given mode,
	 * then awards any qualifying badges.
	 */
	public void checkActivityCountBadges(User user, String activityMode) {
		if (activityMode == null) return;
		db.collection("users").document(user.getUserId())
				.collection("activityLogs")
				.whereEqualTo("transportMode", activityMode)
				.get()
				.addOnSuccessListener(logs -> {
					int count = logs.size();
					db.collection("badges")
							.whereEqualTo("badgeType", Badge.TYPE_ACTIVITY_COUNT)
							.whereEqualTo("activityType", activityMode)
							.get()
							.addOnSuccessListener(badgeDocs -> {
								List<String> earned = user.getEarnedBadgeIds() != null
										? user.getEarnedBadgeIds() : new ArrayList<>();
								for (var doc : badgeDocs.getDocuments()) {
									Badge badge = doc.toObject(Badge.class);
									if (badge == null || earned.contains(badge.getBadgeId())) continue;
									if (count >= badge.getActivityCountRequired()) {
										user.awardBadge(badge, success -> {
											if (success) sendMilestoneNotification(user.getUserId(), badge);
										});
									}
								}
							});
				});
	}

	/**
	 * Sends an in-app notification when a badge milestone is reached.
	 */
	private void sendMilestoneNotification(String userId, Badge badge) {
		Map<String, Object> notification = new HashMap<>();
		notification.put("type", "BADGE_EARNED");
		notification.put("title", "Badge Unlocked: " + badge.getName());
		notification.put("body", badge.getDescription());
		notification.put("badgeId", badge.getBadgeId());
		notification.put("badgeEmoji", badge.getDisplayEmoji());
		notification.put("timestamp", Timestamp.now());

		db.collection("users").document(userId)
				.collection("notifications")
				.add(notification);
	}

	// -------------------------------------------------------------------------
	// Campus-wide and club aggregation
	// -------------------------------------------------------------------------

	/**
	 * Computes the campus-wide aggregated impact summary across all users
	 * for a given time range.
	 *
	 * <p><b>Scalability note:</b> This implementation reads all user documents
	 * and is intended for prototype use only. Production should delegate to a
	 * Cloud Function that maintains a live running total.</p>
	 *
	 * @param from     Start of the time range.
	 * @param to       End of the time range.
	 * @param callback Receives the campus-wide {@link ImpactSummary}.
	 */
	public void getCampusWideImpactSummary(
			Timestamp from, Timestamp to,
			OnSummaryLoadedCallback callback
	) {
		db.collectionGroup("activityLogs")
				.whereGreaterThanOrEqualTo("date", from)
				.whereLessThanOrEqualTo("date", to)
				.get()
				.addOnSuccessListener(querySnapshot -> {
					ImpactSummary summary = new ImpactSummary();
					for (var doc : querySnapshot.getDocuments()) {
						accumulateLog(summary, doc);
					}
					callback.onSummaryLoaded(summary);
				})
				.addOnFailureListener(e -> callback.onSummaryLoaded(null));
	}

	/**
	 * Computes the aggregated impact summary for all members of a specific club.
	 *
	 * <p>Fetches the club's {@code memberIds} list, then queries each member's
	 * logs and sums their contributions.</p>
	 *
	 * @param clubId   Firestore document ID of the club.
	 * @param from     Start of the time range.
	 * @param to       End of the time range.
	 * @param callback Receives the club-level {@link ImpactSummary}.
	 */
	public void getClubImpactSummary(
			String clubId, Timestamp from, Timestamp to,
			OnSummaryLoadedCallback callback
	) {
		db.collection("clubs").document(clubId)
				.get()
				.addOnSuccessListener(documentSnapshot -> {
					Club club = documentSnapshot.toObject(Club.class);
					if (club == null || club.getMemberIds() == null) {
						callback.onSummaryLoaded(null);
						return;
					}

					List<String> memberIds = club.getMemberIds();
					if (memberIds.isEmpty()) {
						callback.onSummaryLoaded(new ImpactSummary());
						return;
					}

					ImpactSummary clubSummary = new ImpactSummary();
					AtomicInteger remaining = new AtomicInteger(memberIds.size());
					for (String memberId : memberIds) {
						getPersonalImpactSummary(memberId, from, to, summary -> {
							if (summary != null) {
								synchronized (clubSummary) {
									mergeSummary(clubSummary, summary);
								}
							}
							if (remaining.decrementAndGet() == 0) {
								callback.onSummaryLoaded(clubSummary);
							}
						});
					}
				})
				.addOnFailureListener(e -> callback.onSummaryLoaded(null));
	}

	/**
	 * Returns the participation rate for a club — the fraction of members
	 * who have logged at least one activity in the current week.
	 *
	 * <p>Shown to club leaders as an anonymised metric (no individual logs
	 * are exposed) to help them nudge inactive members.</p>
	 *
	 * @param clubId   Firestore document ID of the club.
	 * @param callback Receives a value between 0.0 and 1.0.
	 */
	public void getClubParticipationRate(String clubId, OnRateLoadedCallback callback) {
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
		int daysToMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
		cal.add(Calendar.DAY_OF_MONTH, -daysToMonday);
		Timestamp startOfWeek = new Timestamp(cal.getTimeInMillis() / 1000, 0);

		db.collection("clubs").document(clubId)
				.get()
				.addOnSuccessListener(documentSnapshot -> {
					Club club = documentSnapshot.toObject(Club.class);
					if (club == null || club.getMemberIds() == null || club.getMemberIds().isEmpty()) {
						callback.onRateLoaded(0.0);
						return;
					}

					List<String> memberIds = club.getMemberIds();
					AtomicInteger active = new AtomicInteger(0);
					AtomicInteger remaining = new AtomicInteger(memberIds.size());
					for (String memberId : memberIds) {
						db.collection("users").document(memberId)
								.collection("activityLogs")
								.whereGreaterThanOrEqualTo("date", startOfWeek)
								.limit(1)
								.get()
								.addOnSuccessListener(snap -> {
									if (!snap.isEmpty()) active.incrementAndGet();
									if (remaining.decrementAndGet() == 0) {
										callback.onRateLoaded(active.get() / (double) memberIds.size());
									}
								})
								.addOnFailureListener(e -> {
									if (remaining.decrementAndGet() == 0) {
										callback.onRateLoaded(active.get() / (double) memberIds.size());
									}
								});
					}
				})
				.addOnFailureListener(e -> callback.onRateLoaded(0.0));
	}

	// -------------------------------------------------------------------------
	// Campus-goal progress
	// -------------------------------------------------------------------------

	/**
	 * Updates the {@code currentProgress} field of a {@link CampusGoal}
	 * by re-aggregating all relevant user logs and writes the updated value
	 * to Firestore.
	 *
	 * <p>Called by {@link AdminDashboard#triggerGoalRecalculation} when a
	 * calculation metric changes, and periodically by a scheduled Cloud Function.</p>
	 *
	 * @param goal     The campus goal to recalculate.
	 * @param callback Invoked with {@code true} on success.
	 */
	public void recalculateCampusGoalProgress(
			CampusGoal goal,
			User.OnCompleteCallback callback
	) {
		if (goal == null || goal.getGoalId() == null
				|| goal.getStartDate() == null || goal.getEndDate() == null) {
			callback.onComplete(false);
			return;
		}

		getCampusWideImpactSummary(goal.getStartDate(), goal.getEndDate(), summary -> {
			if (summary == null) {
				callback.onComplete(false);
				return;
			}
			double progress = metricValueForGoal(goal, summary);
			goal.setCurrentProgress(progress);
			Map<String, Object> updates = new HashMap<>();
			updates.put("currentProgress", progress);
			if (progress >= goal.getTargetValue()) {
				updates.put("status", CampusGoal.STATUS_COMPLETED);
			}
			db.collection("campusGoals").document(goal.getGoalId())
					.update(updates)
					.addOnSuccessListener(v -> {
						checkCampusGoalMilestones(goal);
						callback.onComplete(true);
					})
					.addOnFailureListener(e -> callback.onComplete(false));
		});
	}

	/**
	 * Checks whether the campus goal has crossed any notification milestones
	 * and, if so, broadcasts a notification to all users.
	 *
	 * @param goal The campus goal after its progress has been updated.
	 */
	private void checkCampusGoalMilestones(CampusGoal goal) {
		if (goal.getNotificationMilestones() == null) return;
		double pct = goal.getProgressPercentage();
		for (int milestone : goal.getNotificationMilestones()) {
			if (pct >= milestone) {
				Map<String, Object> notification = new HashMap<>();
				notification.put("type", "CAMPUS_GOAL_MILESTONE");
				notification.put("goalId", goal.getGoalId());
				notification.put("milestone", milestone);
				notification.put("title", milestone >= 100 ? "Campus goal completed!" : milestone + "% campus goal reached");
				notification.put("body", goal.getTitle());
				notification.put("timestamp", Timestamp.now());
				db.collection("broadcastNotifications")
						.document(goal.getGoalId() + "_" + milestone)
						.set(notification);
			}
		}
	}

	// -------------------------------------------------------------------------
	// Departmental breakdown (used by AdminDashboard)
	// -------------------------------------------------------------------------

	/**
	 * Returns an ordered list of club/department impact summaries, ranked by
	 * total CO2 saved descending, for the admin's departmental breakdown view.
	 *
	 * @param from     Start of the time range.
	 * @param to       End of the time range.
	 * @param callback Receives the ordered list.
	 */
	public void getDepartmentalBreakdown(
			Timestamp from, Timestamp to,
			OnBreakdownLoadedCallback callback
	) {
		db.collection("clubs")
				.get()
				.addOnSuccessListener(querySnapshot -> {
					if (querySnapshot.isEmpty()) {
						callback.onBreakdownLoaded(new ArrayList<>());
						return;
					}

					List<ClubBreakdownEntry> entries = Collections.synchronizedList(new ArrayList<>());
					AtomicInteger remaining = new AtomicInteger(querySnapshot.size());
					for (var doc : querySnapshot.getDocuments()) {
						Club club = doc.toObject(Club.class);
						if (club == null) {
							if (remaining.decrementAndGet() == 0) finishBreakdown(entries, callback);
							continue;
						}
						String id = doc.getId();
						getClubImpactSummary(id, from, to, summary -> {
							ClubBreakdownEntry entry = new ClubBreakdownEntry();
							entry.clubId = id;
							entry.clubName = club.getName() != null ? club.getName() : id;
							entry.summary = summary != null ? summary : new ImpactSummary();
							entries.add(entry);
							if (remaining.decrementAndGet() == 0) finishBreakdown(entries, callback);
						});
					}
				})
				.addOnFailureListener(e -> callback.onBreakdownLoaded(new ArrayList<>()));
	}

	public void getCampusTimeSeries(
			Timestamp from, Timestamp to,
			String granularity, OnTimeSeriesLoadedCallback callback
	) {
		db.collectionGroup("activityLogs")
				.whereGreaterThanOrEqualTo("date", from)
				.whereLessThanOrEqualTo("date", to)
				.get()
				.addOnSuccessListener(querySnapshot -> {
					Map<String, Double> buckets = new TreeMap<>();
					for (var doc : querySnapshot.getDocuments()) {
						Timestamp ts = doc.getTimestamp("date");
						Double co2 = doc.getDouble("co2EquivalentKg");
						if (ts == null || co2 == null) continue;
						String key = bucketLabel(ts, granularity);
						buckets.put(key, buckets.getOrDefault(key, 0.0) + co2);
					}
					callback.onTimeSeriesLoaded(toDataPoints(buckets));
				})
				.addOnFailureListener(e -> callback.onTimeSeriesLoaded(new ArrayList<>()));
	}

	private void finishBreakdown(
			List<ClubBreakdownEntry> entries,
			OnBreakdownLoadedCallback callback
	) {
		entries.sort(Comparator.comparingDouble(
				(ClubBreakdownEntry e) -> e.summary.totalCO2SavedKg).reversed());
		callback.onBreakdownLoaded(entries);
	}

	private void accumulateLog(ImpactSummary summary, DocumentSnapshot doc) {
		String category = doc.getString("category");
		Double co2 = doc.getDouble("co2EquivalentKg");
		Double points = doc.getDouble("pointsEarned");
		if (co2 != null) summary.totalCO2SavedKg += co2;
		if (points != null) summary.totalPointsEarned += points;

		if (ActivityLog.CATEGORY_WASTE.equals(category)) {
			Double waste = doc.getDouble("wasteKg");
			if (waste != null) summary.totalWasteDivertedKg += waste;
		} else if (ActivityLog.CATEGORY_ENERGY.equals(category)) {
			Double energy = doc.getDouble("energyKwh");
			if (energy != null) summary.totalEnergySavedKwh += energy;
		}
	}

	private void mergeSummary(ImpactSummary target, ImpactSummary source) {
		target.totalCO2SavedKg += source.totalCO2SavedKg;
		target.totalWasteDivertedKg += source.totalWasteDivertedKg;
		target.totalEnergySavedKwh += source.totalEnergySavedKwh;
		target.totalPointsEarned += source.totalPointsEarned;
	}

	private double metricValueForGoal(CampusGoal goal, ImpactSummary summary) {
		if (CampusGoal.METRIC_WASTE.equals(goal.getMetricType())) {
			return summary.totalWasteDivertedKg;
		}
		if (CampusGoal.METRIC_ENERGY.equals(goal.getMetricType())) {
			return summary.totalEnergySavedKwh;
		}
		return summary.totalCO2SavedKg;
	}

	private String bucketLabel(Timestamp timestamp, String granularity) {
		Date date = timestamp.toDate();
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		if ("yearly".equalsIgnoreCase(granularity)) {
			return String.valueOf(cal.get(Calendar.YEAR));
		}
		if ("weekly".equalsIgnoreCase(granularity)) {
			return cal.get(Calendar.YEAR) + "-W" + String.format("%02d", cal.get(Calendar.WEEK_OF_YEAR));
		}
		if ("daily".equalsIgnoreCase(granularity)) {
			return String.format("%04d-%02d-%02d",
					cal.get(Calendar.YEAR),
					cal.get(Calendar.MONTH) + 1,
					cal.get(Calendar.DAY_OF_MONTH));
		}
		return String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
	}

	private List<DataPoint> toDataPoints(Map<String, Double> buckets) {
		List<DataPoint> points = new ArrayList<>();
		for (Map.Entry<String, Double> entry : buckets.entrySet()) {
			points.add(new DataPoint(entry.getKey(), entry.getValue()));
		}
		return points;
	}

	// -------------------------------------------------------------------------
	// Inner data classes
	// -------------------------------------------------------------------------

	/**
	 * Aggregated sustainability impact totals for a user, club, or the whole campus.
	 */
	public static class ImpactSummary {
		/**
		 * Total CO2 saved in kilograms.
		 */
		public double totalCO2SavedKg = 0.0;
		/**
		 * Total waste diverted from landfill in kilograms.
		 */
		public double totalWasteDivertedKg = 0.0;
		/**
		 * Total energy saved in kWh.
		 */
		public double totalEnergySavedKwh = 0.0;
		/**
		 * Total green points earned.
		 */
		public double totalPointsEarned = 0.0;
	}

	/**
	 * A single time-series data point for chart rendering.
	 */
	public static class DataPoint {
		/**
		 * Label for the X axis (e.g. "Mon", "Week 3", "March").
		 */
		public String label;
		/**
		 * Numeric value for the Y axis (e.g. CO2 saved that day).
		 */
		public double value;

		public DataPoint(String label, double value) {
			this.label = label;
			this.value = value;
		}
	}

	/**
	 * Per-club impact entry for the departmental breakdown list.
	 */
	public static class ClubBreakdownEntry {
		public String clubId;
		public String clubName;
		public ImpactSummary summary;
	}

	// -------------------------------------------------------------------------
	// Callback interfaces
	// -------------------------------------------------------------------------

	/**
	 * Callback for impact summary operations.
	 */
	public interface OnSummaryLoadedCallback {
		/**
		 * @param summary The computed summary, or null on failure.
		 */
		void onSummaryLoaded(ImpactSummary summary);
	}

	/**
	 * Callback for time-series chart data.
	 */
	public interface OnTimeSeriesLoadedCallback {
		/**
		 * @param dataPoints Ordered list of data points; empty on failure.
		 */
		void onTimeSeriesLoaded(List<DataPoint> dataPoints);
	}

	/**
	 * Callback for milestone checking.
	 */
	public interface OnMilestonesCheckedCallback {
		/**
		 * @param newBadges Badges awarded in this check; may be empty.
		 */
		void onMilestonesChecked(List<Badge> newBadges);
	}

	/**
	 * Callback for participation rate.
	 */
	public interface OnRateLoadedCallback {
		/**
		 * @param rate Value between 0.0 and 1.0.
		 */
		void onRateLoaded(double rate);
	}

	/**
	 * Callback for departmental breakdown.
	 */
	public interface OnBreakdownLoadedCallback {
		/**
		 * @param breakdown Ordered list of club entries; empty on failure.
		 */
		void onBreakdownLoaded(List<ClubBreakdownEntry> breakdown);
	}
}
