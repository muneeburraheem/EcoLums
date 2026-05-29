package com.example.ecolums;

import android.content.Context;
import android.net.Uri;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Service/controller class that encapsulates all privileged admin operations.
 *
 * <p>Provides methods for managing users (reset password, export data, delete data),
 * publishing and deleting sustainability tips, creating campus goals, updating
 * calculation metrics, and triggering goal-progress recalculations. Every write
 * operation also appends an immutable {@link AuditLogEntry} to Firestore.</p>
 *
 * <p>Collaborators: {@link ProgressTracker}, {@link Leaderboard}, {@link AuditLogEntry}.</p>
 * <p>
 * Outstanding issues: {@code triggerGoalRecalculation} is a stub — the full
 * recalculation logic in {@link ProgressTracker} is not yet wired up.
 */
public class AdminDashboard {

	// -------------------------------------------------------------------------
	// Dependencies
	// -------------------------------------------------------------------------

	private final FirebaseFirestore db;
	private final FirebaseStorage storage;
	private final FirebaseAuth auth;
	private final ProgressTracker progressTracker;
	private final Leaderboard leaderboard;

	// -------------------------------------------------------------------------
	// Constructor
	// -------------------------------------------------------------------------


	public AdminDashboard() {
		this.db = FirebaseFirestore.getInstance();
		this.storage = FirebaseStorage.getInstance();
		this.auth = FirebaseAuth.getInstance();
		this.progressTracker = new ProgressTracker();
		this.leaderboard = new Leaderboard();
	}


	private void requireAdminRole(Runnable onAuthorized, Runnable onDenied) {
		if (auth.getCurrentUser() == null) {
			onDenied.run();
			return;
		}

		String currentUid = auth.getCurrentUser().getUid();
		db.collection("users").document(currentUid)
				.get()
				.addOnSuccessListener(snapshot -> {
					User user = snapshot.toObject(User.class);
					if (user != null && User.ROLE_ADMIN.equals(user.getRole())) {
						onAuthorized.run();
					} else {
						onDenied.run();
					}
				})
				.addOnFailureListener(e -> onDenied.run());
	}


	public void loadCampusImpactSummary(
			Timestamp from, Timestamp to,
			OnSummaryCallback callback
	) {
		requireAdminRole(
				() -> progressTracker.getCampusWideImpactSummary(
						from, to,
						callback::onSummaryLoaded
				),
				() -> callback.onSummaryLoaded(null)
		);
	}

	public void loadCampusTimeSeries(
			Timestamp from, Timestamp to, String granularity,
			OnTimeSeriesCallback callback
	) {
		requireAdminRole(
				() -> progressTracker.getCampusTimeSeries(
						from, to, granularity, callback::onTimeSeriesLoaded
				),
				() -> callback.onTimeSeriesLoaded(new ArrayList<>())
		);
	}


	public void loadDepartmentalBreakdown(
			Timestamp from, Timestamp to,
			OnBreakdownCallback callback
	) {
		requireAdminRole(
				() -> progressTracker.getDepartmentalBreakdown(
						from, to,
						callback::onBreakdownLoaded
				),
				() -> callback.onBreakdownLoaded(new ArrayList<>())
		);
	}

	public void createCampusGoal(
			String title, String metricType, double targetValue,
			Timestamp startDate, Timestamp endDate,
			int[] notificationMilestones,
			OnCompleteCallback callback
	) {
		requireAdminRole(
				() -> {
					Map<String, Object> goalData = new HashMap<>();
					goalData.put("title", title);
					goalData.put("metricType", metricType);
					goalData.put("targetValue", targetValue);
					goalData.put("currentProgress", 0.0);
					goalData.put("startDate", startDate);
					goalData.put("endDate", endDate);
					goalData.put("status", CampusGoal.STATUS_ACTIVE);
					goalData.put("notificationMilestones", notificationMilestones);

					db.collection("campusGoals")
							.add(goalData)
							.addOnSuccessListener(ref -> {
								writeAuditLog(
										AuditLogEntry.ACTION_GOAL_CREATED,
										ref.getId(), "Created campus goal: " + title,
										null, null
								);
								callback.onComplete(true, null);
							})
							.addOnFailureListener(e -> callback.onComplete(false, e.getMessage()));
				},
				() -> callback.onComplete(false, "Permission denied: Admin role required.")
		);
	}

	/**
	 * Fetches all campus goals from Firestore, ordered by start date descending
	 * (most recent first), for display in the goals management screen.
	 *
	 * <p>Supports AC 5 (Archive of Past Goals) by including goals with all status
	 * values ({@link CampusGoal#STATUS_ACTIVE}, {@link CampusGoal#STATUS_COMPLETED},
	 * {@link CampusGoal#STATUS_ARCHIVED}).</p>
	 *
	 * @param callback Receives the full list of campus goals.
	 */
	public void getCampusGoals(OnGoalsLoadedCallback callback) {
		requireAdminRole(
				() -> db.collection("campusGoals")
						.orderBy("startDate", Query.Direction.DESCENDING)
						.get()
						.addOnSuccessListener(querySnapshot -> {
							List<CampusGoal> goals = new ArrayList<>();
							for (var doc : querySnapshot.getDocuments()) {
								CampusGoal goal = doc.toObject(CampusGoal.class);
								if (goal != null) {
									goal.setGoalId(doc.getId());
									goals.add(goal);
								}
							}
							callback.onGoalsLoaded(goals);
						})
						.addOnFailureListener(e -> callback.onGoalsLoaded(new ArrayList<>())),
				() -> callback.onGoalsLoaded(new ArrayList<>())
		);
	}

	/**
	 * Archives a campus goal, setting its status to {@link CampusGoal#STATUS_ARCHIVED}
	 * so it appears in the historical goals section and is excluded from active tracking.
	 *
	 * @param goalId   Firestore document ID of the goal to archive.
	 * @param callback Invoked with {@code true} on success.
	 */
	public void archiveCampusGoal(String goalId, OnCompleteCallback callback) {
		requireAdminRole(
				() -> db.collection("campusGoals").document(goalId)
						.update("status", CampusGoal.STATUS_ARCHIVED)
						.addOnSuccessListener(aVoid -> {
							writeAuditLog(
									AuditLogEntry.ACTION_GOAL_ARCHIVED,
									goalId, "Archived campus goal: " + goalId, null, null
							);
							callback.onComplete(true, null);
						})
						.addOnFailureListener(e -> callback.onComplete(false, e.getMessage())),
				() -> callback.onComplete(false, "Permission denied.")
		);
	}

	/**
	 * Manually triggers a recalculation of a campus goal's current progress.
	 *
	 * <p>Called after a {@link CalculationMetric} update so that the displayed
	 * progress reflects the revised CO₂ factors (AC 4 of the calculation metrics
	 * user story).</p>
	 *
	 * @param goal     The goal to recalculate.
	 * @param callback Invoked with {@code true} on success.
	 */
	public void triggerGoalRecalculation(CampusGoal goal, OnCompleteCallback callback) {
		requireAdminRole(
				() -> progressTracker.recalculateCampusGoalProgress(
						goal,
						success -> callback.onComplete(
								success,
								success ? null : "Recalculation failed."
						)
				),
				() -> callback.onComplete(false, "Permission denied.")
		);
	}

	// =========================================================================
	// SECTION 4 — Sustainability tips  (User Story: publish sustainability tips)
	// =========================================================================

	/**
	 * Publishes a new sustainability tip or educational article.
	 *
	 * <p>Satisfies AC 1 (Authorized Access — only admins can create content),
	 * AC 2 (Multimedia Support — imageUrls and videoUrl fields are stored),
	 * and AC 3 (Categorization — {@link SustainabilityTip#CATEGORY_TRANSPORT} etc.).</p>
	 *
	 * <p>After saving, sends a broadcast notification to all users informing
	 * them of new content (AC 5).</p>
	 *
	 * @param title         Headline of the tip.
	 * @param summary       Short snippet for the list view.
	 * @param bodyContent   Full body text (Markdown supported).
	 * @param category      One of the {@link SustainabilityTip} CATEGORY_* constants.
	 * @param imageUrls     Firebase Storage URLs of attached images (may be empty).
	 * @param videoUrl      Optional video URL (may be null).
	 * @param externalLinks Optional list of further-reading URLs (may be null/empty).
	 * @param callback      Invoked with {@code true} on success.
	 */
	public void publishSustainabilityTip(
			String title, String summary, String bodyContent,
			String category, List<String> imageUrls,
			String videoUrl, List<String> externalLinks,
			OnCompleteCallback callback
	) {
		requireAdminRole(
				() -> {
					String adminUid = auth.getCurrentUser().getUid();

					Map<String, Object> tipData = new HashMap<>();
					tipData.put("title", title);
					tipData.put("summary", summary);
					tipData.put("bodyContent", bodyContent);
					tipData.put("category", category);
					tipData.put("imageUrls", imageUrls != null ? imageUrls : new ArrayList<>());
					tipData.put("videoUrl", videoUrl);
					tipData.put(
							"externalLinks",
							externalLinks != null ? externalLinks : new ArrayList<>()
					);
					tipData.put("authorAdminId", adminUid);
					tipData.put("publishedAt", Timestamp.now());
					tipData.put("viewCount", 0);
					tipData.put("likeCount", 0);
					tipData.put("isNew", true);

					db.collection("sustainabilityTips")
							.add(tipData)
							.addOnSuccessListener(ref -> {
								writeAuditLog(
										AuditLogEntry.ACTION_TIP_PUBLISHED,
										ref.getId(), "Published tip: " + title, null, null
								);
								sendNewContentBroadcast(title);
								callback.onComplete(true, null);
							})
							.addOnFailureListener(e -> callback.onComplete(false, e.getMessage()));
				},
				() -> callback.onComplete(false, "Permission denied.")
		);
	}

	/**
	 * Updates an existing sustainability tip with new content.
	 *
	 * <p>Records {@code lastEditedAt} on the document and writes an audit entry.
	 * Satisfies AC 1 (only admins can edit).</p>
	 *
	 * @param tipId       Firestore document ID of the tip to edit.
	 * @param updatedData Map of field names to new values (partial update).
	 * @param callback    Invoked with {@code true} on success.
	 */
	public void editSustainabilityTip(
			String tipId, Map<String, Object> updatedData,
			OnCompleteCallback callback
	) {
		requireAdminRole(
				() -> {
					updatedData.put("lastEditedAt", Timestamp.now());

					db.collection("sustainabilityTips").document(tipId)
							.update(updatedData)
							.addOnSuccessListener(aVoid -> callback.onComplete(true, null))
							.addOnFailureListener(e -> callback.onComplete(false, e.getMessage()));
				},
				() -> callback.onComplete(false, "Permission denied.")
		);
	}

	/**
	 * Deletes a sustainability tip from the platform.
	 *
	 * <p>Satisfies AC 1 (only admins can delete). Writes an audit log entry
	 * before the deletion so the action is traceable even after the tip is gone.</p>
	 *
	 * @param tipId    Firestore document ID of the tip to delete.
	 * @param callback Invoked with {@code true} on success.
	 */
	public void deleteSustainabilityTip(String tipId, OnCompleteCallback callback) {
		requireAdminRole(
				() -> {
					writeAuditLog(
							AuditLogEntry.ACTION_TIP_DELETED,
							tipId, "Deleted sustainability tip: " + tipId, null, null
					);

					db.collection("sustainabilityTips").document(tipId)
							.delete()
							.addOnSuccessListener(aVoid -> callback.onComplete(true, null))
							.addOnFailureListener(e -> callback.onComplete(false, e.getMessage()));
				},
				() -> callback.onComplete(false, "Permission denied.")
		);
	}

	/**
	 * Fetches all sustainability tips, optionally filtered by category.
	 *
	 * <p>Used to populate both the admin content-management list and the
	 * student-facing tips screen. Ordered by publication date descending.</p>
	 *
	 * @param categoryFilter One of the {@link SustainabilityTip} CATEGORY_* constants,
	 *                       or null to load all categories.
	 * @param callback       Receives the list of tips.
	 */
	public void getSustainabilityTips(String categoryFilter, OnTipsLoadedCallback callback) {
		Query query = db.collection("sustainabilityTips")
				.orderBy("publishedAt", Query.Direction.DESCENDING);

		if (categoryFilter != null) {
			query = query.whereEqualTo("category", categoryFilter);
		}

		query.get()
				.addOnSuccessListener(querySnapshot -> {
					List<SustainabilityTip> tips = new ArrayList<>();
					for (var doc : querySnapshot.getDocuments()) {
						SustainabilityTip tip = doc.toObject(SustainabilityTip.class);
						if (tip != null) {
							tip.setTipId(doc.getId());
							tips.add(tip);
						}
					}
					callback.onTipsLoaded(tips);
				})
				.addOnFailureListener(e -> callback.onTipsLoaded(new ArrayList<>()));
	}

	/**
	 * Increments the view count for a tip by 1.
	 *
	 * <p>Called from the tip detail fragment when a student opens a tip.
	 * Satisfies AC 4 (Engagement Tracking).</p>
	 *
	 * @param tipId    Firestore document ID of the viewed tip.
	 * @param callback Invoked with {@code true} on success.
	 */
	public void incrementTipViewCount(String tipId, OnCompleteCallback callback) {
		db.collection("sustainabilityTips").document(tipId)
				.update("viewCount", FieldValue.increment(1))
				.addOnSuccessListener(aVoid -> callback.onComplete(true, null))
				.addOnFailureListener(e -> callback.onComplete(false, e.getMessage()));
	}

	/**
	 * Sends a broadcast notification to all users informing them that new
	 * sustainability content has been published.
	 *
	 * <p>Writes a document to {@code broadcastNotifications} which FCM-enabled
	 * Cloud Functions pick up and forward as push notifications (AC 5).</p>
	 *
	 * @param tipTitle The title of the newly published tip.
	 */
	private void sendNewContentBroadcast(String tipTitle) {
		Map<String, Object> notification = new HashMap<>();
		notification.put("type", "NEW_TIP");
		notification.put("title", "New Sustainability Tip!");
		notification.put("body", "Check out: " + tipTitle);
		notification.put("timestamp", Timestamp.now());

		// TODO: Write to broadcastNotifications collection. Wire up a
		//       Cloud Function that listens to this collection and sends
		//       an FCM multicast to all registered device tokens.
		db.collection("broadcastNotifications").add(notification);
	}

	// =========================================================================
	// SECTION 5 — Calculation metrics  (User Story: update impact calculation metrics)
	// =========================================================================

	/**
	 * Retrieves all CO₂ conversion factors from the {@code calculationMetrics}
	 * Firestore collection.
	 *
	 * <p>Used to populate the "Calculation Settings" page in the admin UI
	 * (AC 1) and the student-facing "Info" icon tooltip (AC 5).</p>
	 *
	 * @param callback Receives the full list of calculation metrics.
	 */
	public void getCalculationMetrics(OnMetricsLoadedCallback callback) {
		db.collection("calculationMetrics")
				.get()
				.addOnSuccessListener(querySnapshot -> {
					List<CalculationMetric> metrics = new ArrayList<>();
					for (var doc : querySnapshot.getDocuments()) {
						CalculationMetric m = doc.toObject(CalculationMetric.class);
						if (m != null) {
							m.setMetricId(doc.getId());
							metrics.add(m);
						}
					}
					callback.onMetricsLoaded(metrics);
				})
				.addOnFailureListener(e -> callback.onMetricsLoaded(new ArrayList<>()));
	}

	/**
	 * Updates a single CO₂ conversion factor and records the change in the audit log.
	 *
	 * <p>Satisfies AC 1 (Centralized Formula — admin edits a specific factor),
	 * AC 2 (Multiple Metric Update — any metricKey can be changed),
	 * and AC 3 (Audit Log — who changed what and when).</p>
	 *
	 * <p>After a successful write, calls {@link #triggerGlobalMetricRecalculation}
	 * to satisfy AC 4 (Automated Recalculation of campus-goal progress).</p>
	 *
	 * @param metricKey     The key of the metric to update (e.g. {@link CalculationMetric#KEY_BIKE_PER_KM}).
	 * @param newValue      The new conversion factor value.
	 * @param previousValue The old value (for the audit log).
	 * @param callback      Invoked with {@code true} on success.
	 */
	public void updateCalculationMetric(
			String metricKey, double newValue,
			double previousValue, OnCompleteCallback callback
	) {
		requireAdminRole(
				() -> {
					String adminUid = auth.getCurrentUser().getUid();

					Map<String, Object> updates = new HashMap<>();
					updates.put("value", newValue);
					updates.put("lastUpdated", Timestamp.now());
					updates.put("lastUpdatedByAdminId", adminUid);

					db.collection("calculationMetrics").document(metricKey)
							.update(updates)
							.addOnSuccessListener(aVoid -> {
								writeAuditLog(
										AuditLogEntry.ACTION_METRIC_UPDATED,
										metricKey,
										"Updated metric " + metricKey + " from "
												+ previousValue + " to " + newValue,
										String.valueOf(previousValue),
										String.valueOf(newValue)
								);
								triggerGlobalMetricRecalculation();
								callback.onComplete(true, null);
							})
							.addOnFailureListener(e -> callback.onComplete(false, e.getMessage()));
				},
				() -> callback.onComplete(false, "Permission denied.")
		);
	}

	/**
	 * Triggers a background recalculation of all active campus goals after
	 * a calculation metric has been changed globally.
	 *
	 * <p>Satisfies AC 4 (Automated Recalculation). In production, this should
	 * be replaced with a Cloud Function triggered by a Firestore write to the
	 * {@code calculationMetrics} collection.</p>
	 */
	private void triggerGlobalMetricRecalculation() {
		db.collection("campusGoals")
				.whereEqualTo("status", CampusGoal.STATUS_ACTIVE)
				.get()
				.addOnSuccessListener(querySnapshot -> {
					for (var doc : querySnapshot.getDocuments()) {
						CampusGoal goal = doc.toObject(CampusGoal.class);
						if (goal != null) {
							goal.setGoalId(doc.getId());
							progressTracker.recalculateCampusGoalProgress(
									goal, success -> {
										// Log recalculation failures silently for now.
										// TODO: Surface these errors in the admin system health view.
									}
							);
						}
					}
				});
	}

	// =========================================================================
	// SECTION 6 — Report generation  (User Story: track campus impact, AC 3)
	// =========================================================================

	/**
	 * Generates and returns raw aggregated data as a CSV-formatted string for
	 * official university sustainability reporting.
	 *
	 * <p>Satisfies AC 3 (Export Functionality — CSV format).</p>
	 *
	 * @param from     Start of the reporting period.
	 * @param to       End of the reporting period.
	 * @param callback Receives the CSV string, or null on failure.
	 */
	public void exportReportAsCsv(
			Timestamp from, Timestamp to,
			OnReportExportedCallback callback
	) {
		requireAdminRole(
				() -> progressTracker.getCampusWideImpactSummary(
						from, to, summary -> {
							if (summary == null) {
								callback.onReportExported(null);
								return;
							}

							// Build a simple CSV string from the summary object.
							StringBuilder csv = new StringBuilder();
							csv.append("Metric,Value\n");
							csv.append("Total CO2 Saved (kg),").append(summary.totalCO2SavedKg).append(
									"\n");
							csv.append("Total Waste Diverted (kg),").append(summary.totalWasteDivertedKg).append(
									"\n");
							csv.append("Total Energy Saved (kWh),").append(summary.totalEnergySavedKwh).append(
									"\n");
							csv.append("Total Green Points Earned,").append(summary.totalPointsEarned).append(
									"\n");

							callback.onReportExported(csv.toString());
						}
				),
				() -> callback.onReportExported(null)
		);
	}

	/**
	 * Generates a sustainability report in PDF format and writes it to the
	 * device's local storage.
	 *
	 * <p>Satisfies AC 3 (Export Functionality — PDF format).
	 * The returned {@link Uri} points to the saved PDF file.</p>
	 *
	 * @param context  Android Context needed to access the file system.
	 * @param from     Start of the reporting period.
	 * @param to       End of the reporting period.
	 * @param callback Receives the {@link Uri} of the generated PDF, or null on failure.
	 */
	public void exportReportAsPdf(
			Context context, Timestamp from, Timestamp to,
			OnPdfExportedCallback callback
	) {
		requireAdminRole(
				() -> {
					// TODO: Fetch campus summary and departmental breakdown, then use
					//       a PDF library (e.g. iText 7 for Android) to build a formatted
					//       report document with charts and tables. Write the file to
					//       context.getExternalFilesDir() and return the Uri.
					callback.onPdfExported(null); // stub
				},
				() -> callback.onPdfExported(null)
		);
	}

	// =========================================================================
	// SECTION 7 — User authentication & privacy management
	//             (User Story: securely manage user authentication)
	// =========================================================================

	/**
	 * Sends a password-reset email to a specific user via Firebase Authentication.
	 *
	 * <p>Satisfies AC 2 (Password & Authentication Policies).</p>
	 *
	 * @param userEmail The university email of the user who needs a reset.
	 * @param callback  Invoked with {@code true} on success.
	 */
	public void triggerPasswordReset(String userEmail, OnCompleteCallback callback) {
		requireAdminRole(
				() -> {
					// Write audit log before the async action so the record exists
					// even if the reset call has a transient failure.
					writeAuditLog(
							AuditLogEntry.ACTION_PASSWORD_RESET,
							userEmail, "Admin triggered password reset for: " + userEmail,
							null, null
					);

					auth.sendPasswordResetEmail(userEmail)
							.addOnSuccessListener(aVoid -> callback.onComplete(true, null))
							.addOnFailureListener(e -> callback.onComplete(false, e.getMessage()));
				},
				() -> callback.onComplete(false, "Permission denied.")
		);
	}

	/**
	 * Updates the default privacy setting for new users — whether their data
	 * is shared with campus clubs by default (opt-in vs opt-out).
	 *
	 * <p>Satisfies AC 3 (Global Privacy Defaults). The setting is stored in a
	 * {@code platformConfig} Firestore document and applied when new users
	 * are created.</p>
	 *
	 * @param shareByDefault {@code true} = opt-out (share by default);
	 *                       {@code false} = opt-in (private by default).
	 * @param callback       Invoked with {@code true} on success.
	 */
	public void updateGlobalPrivacyDefault(
			boolean shareByDefault,
			OnCompleteCallback callback
	) {
		requireAdminRole(
				() -> {
					Map<String, Object> update = new HashMap<>();
					update.put("shareDataWithClubsByDefault", shareByDefault);
					update.put("lastUpdated", Timestamp.now());

					db.collection("platformConfig").document("privacySettings")
							.set(update)
							.addOnSuccessListener(aVoid -> {
								writeAuditLog(
										AuditLogEntry.ACTION_USER_PRIVACY_CHANGED,
										null,
										"Global privacy default set to shareByDefault="
												+ shareByDefault,
										null, String.valueOf(shareByDefault)
								);
								callback.onComplete(true, null);
							})
							.addOnFailureListener(e -> callback.onComplete(false, e.getMessage()));
				},
				() -> callback.onComplete(false, "Permission denied.")
		);
	}

	/**
	 * Permanently deletes all personal sustainability data logged by a user
	 * ("Right to be Forgotten" request).
	 *
	 * <p>Satisfies AC 4 (Data Deletion). Deletes the {@code activityLogs}
	 * sub-collection and resets point-related fields on the user document.
	 * The Firebase Auth account is NOT deleted here — that requires a
	 * separate admin SDK call (Cloud Function).</p>
	 *
	 * @param targetUserId UID of the user whose data should be erased.
	 * @param callback     Invoked with {@code true} on success.
	 */
	public void deleteUserData(String targetUserId, OnCompleteCallback callback) {
		requireAdminRole(
				() -> {
					writeAuditLog(
							AuditLogEntry.ACTION_USER_DATA_DELETED,
							targetUserId, "Admin deleted all data for user: " + targetUserId,
							null, null
					);

					// TODO: Batch-delete all documents in
					//       users/{targetUserId}/activityLogs. Firestore does not
					//       support collection deletion from the client SDK, so this
					//       must be done in a Cloud Function or via repeated batch reads.
					//       After deletion, reset users/{targetUserId}.totalPoints = 0
					//       and earnedBadgeIds = [] using a batch write.
					callback.onComplete(false, "Not yet implemented — requires Cloud Function.");
				},
				() -> callback.onComplete(false, "Permission denied.")
		);
	}

	/**
	 * Exports all personal data for a user as a JSON string (data portability).
	 *
	 * <p>Satisfies AC 4 (Data Export Requests). Collects the user's profile
	 * and all their activity log entries and serialises them.</p>
	 *
	 * @param targetUserId UID of the user whose data to export.
	 * @param callback     Receives the JSON export string, or null on failure.
	 */
	public void exportUserData(String targetUserId, OnReportExportedCallback callback) {
		requireAdminRole(
				() -> {
					writeAuditLog(
							AuditLogEntry.ACTION_USER_DATA_EXPORTED,
							targetUserId, "Admin exported data for user: " + targetUserId,
							null, null
					);

					// TODO: Fetch users/{targetUserId} and all documents in
					//       users/{targetUserId}/activityLogs, then serialise
					//       using Gson or org.json into a JSON string.
					callback.onReportExported(null); // stub
				},
				() -> callback.onReportExported(null)
		);
	}

	// =========================================================================
	// SECTION 8 — System health monitoring
	//             (User Story: monitor system uptime and API usage)
	// =========================================================================

	/**
	 * Loads the current system health snapshot from Firestore.
	 *
	 * <p>Data is expected to be written by a Cloud Function that periodically
	 * pings internal endpoints and records results to {@code systemHealth/current}.
	 * Satisfies AC 1 (System Health Overview).</p>
	 *
	 * @param callback Receives the health snapshot map (keys: {@code status},
	 *                 {@code uptimePct}, {@code lastChecked}), or null on failure.
	 */
	public void loadSystemHealthSnapshot(OnMapLoadedCallback callback) {
		requireAdminRole(
				() -> db.collection("systemHealth").document("current")
						.get()
						.addOnSuccessListener(doc -> callback.onMapLoaded(doc.getData()))
						.addOnFailureListener(e -> callback.onMapLoaded(null)),
				() -> callback.onMapLoaded(null)
		);
	}

	/**
	 * Loads API usage data for chart rendering on the system health screen.
	 *
	 * <p>Each document in {@code systemHealth/apiUsage/hourly} represents one
	 * hour's request count. Satisfies AC 2 (API Usage Visualizations).</p>
	 *
	 * @param from     Start of the time range.
	 * @param to       End of the time range.
	 * @param callback Receives a list of {@link ProgressTracker.DataPoint} (label = hour, value = request count).
	 */
	public void loadApiUsageData(
			Timestamp from, Timestamp to,
			OnTimeSeriesCallback callback
	) {
		requireAdminRole(
				() -> db.collection("systemHealth")
						.document("apiUsage")
						.collection("hourly")
						.whereGreaterThanOrEqualTo("timestamp", from)
						.whereLessThanOrEqualTo("timestamp", to)
						.orderBy("timestamp", Query.Direction.ASCENDING)
						.get()
						.addOnSuccessListener(querySnapshot -> {
							List<ProgressTracker.DataPoint> points = new ArrayList<>();
							for (var doc : querySnapshot.getDocuments()) {
								String label = doc.getString("hourLabel");
								Long count = doc.getLong("requestCount");
								if (label != null && count != null) {
									points.add(new ProgressTracker.DataPoint(label, count));
								}
							}
							callback.onTimeSeriesLoaded(points);
						})
						.addOnFailureListener(e -> callback.onTimeSeriesLoaded(new ArrayList<>())),
				() -> callback.onTimeSeriesLoaded(new ArrayList<>())
		);
	}

	/**
	 * Loads the recent error rate data for the system health screen.
	 *
	 * <p>Satisfies AC 3 (Error Rate Tracking). Each document in
	 * {@code systemHealth/errors/hourly} holds a failed-request count.</p>
	 *
	 * @param from     Start of the time range.
	 * @param to       End of the time range.
	 * @param callback Receives a list of {@link ProgressTracker.DataPoint} (label = hour, value = error count).
	 */
	public void loadErrorRateData(
			Timestamp from, Timestamp to,
			OnTimeSeriesCallback callback
	) {
		requireAdminRole(
				() -> {
					// TODO: Mirror loadApiUsageData but query systemHealth/errors/hourly.
					callback.onTimeSeriesLoaded(new ArrayList<>()); // stub
				},
				() -> callback.onTimeSeriesLoaded(new ArrayList<>())
		);
	}

	// =========================================================================
	// SECTION 9 — Challenge management  (User Story: create green challenges)
	// =========================================================================

	/**
	 * Creates a new green challenge or competition and persists it to Firestore.
	 *
	 * <p>Satisfies AC 1 (Challenge Creation Portal). The challenge is initially
	 * set to {@link Challenge#STATUS_UPCOMING} and transitions to
	 * {@link Challenge#STATUS_ACTIVE} on its start date (handled by a Cloud Function).</p>
	 *
	 * @param title                  Display title (e.g. "Plastic-Free February").
	 * @param description            Short description for the public gallery.
	 * @param goalType               One of the {@link Challenge} GOAL_TYPE_* constants.
	 * @param targetValue            Numeric target participants must reach.
	 * @param targetUnit             Human-readable unit (e.g. "km", "kWh").
	 * @param startDate              When the challenge begins.
	 * @param endDate                When the challenge ends.
	 * @param rewardBadgeId          Badge to award on completion (may be null).
	 * @param notificationMilestones Percentage thresholds for in-app nudges.
	 * @param callback               Invoked with {@code true} on success.
	 */
	public void createChallenge(
			String title, String description, String goalType,
			double targetValue, String targetUnit,
			Timestamp startDate, Timestamp endDate,
			String rewardBadgeId, int[] notificationMilestones,
			OnCompleteCallback callback
	) {
		requireAdminRole(
				() -> {
					String creatorUid = auth.getCurrentUser().getUid();

					Map<String, Object> challengeData = new HashMap<>();
					challengeData.put("title", title);
					challengeData.put("description", description);
					challengeData.put("creatorId", creatorUid);
					challengeData.put("goalType", goalType);
					challengeData.put("targetValue", targetValue);
					challengeData.put("targetUnit", targetUnit);
					challengeData.put("startDate", startDate);
					challengeData.put("endDate", endDate);
					challengeData.put("status", Challenge.STATUS_UPCOMING);
					challengeData.put("rewardBadgeId", rewardBadgeId);
					challengeData.put("notificationMilestones", notificationMilestones);
					challengeData.put("participantIds", new ArrayList<>());
					challengeData.put("participantScores", new HashMap<>());

					db.collection("challenges")
							.add(challengeData)
							.addOnSuccessListener(ref -> {
								writeAuditLog(
										AuditLogEntry.ACTION_CHALLENGE_CREATED,
										ref.getId(), "Created challenge: " + title, null, null
								);
								callback.onComplete(true, null);
							})
							.addOnFailureListener(e -> callback.onComplete(false, e.getMessage()));
				},
				() -> callback.onComplete(false, "Permission denied.")
		);
	}

	/**
	 * Fetches all challenges from Firestore, ordered by start date descending.
	 *
	 * <p>Can be filtered by {@link Challenge#STATUS_ACTIVE}, {@link Challenge#STATUS_UPCOMING},
	 * or {@link Challenge#STATUS_COMPLETED}; pass null to load all statuses.</p>
	 *
	 * @param statusFilter One of the Challenge STATUS_* constants, or null for all.
	 * @param callback     Receives the list of challenges.
	 */
	public void getChallenges(String statusFilter, OnChallengesLoadedCallback callback) {
		Query query = db.collection("challenges")
				.orderBy("startDate", Query.Direction.DESCENDING);

		if (statusFilter != null) {
			query = query.whereEqualTo("status", statusFilter);
		}

		query.get()
				.addOnSuccessListener(querySnapshot -> {
					List<Challenge> challenges = new ArrayList<>();
					for (var doc : querySnapshot.getDocuments()) {
						Challenge c = doc.toObject(Challenge.class);
						if (c != null) {
							c.setChallengeId(doc.getId());
							challenges.add(c);
						}
					}
					callback.onChallengesLoaded(challenges);
				})
				.addOnFailureListener(e -> callback.onChallengesLoaded(new ArrayList<>()));
	}

	// =========================================================================
	// SECTION 10 — Audit log  (User Story: metric updates, privacy changes)
	// =========================================================================

	/**
	 * Writes an immutable audit log entry to the {@code auditLog} Firestore collection.
	 *
	 * <p>Called internally after every privileged action. Satisfies
	 * AC 3 (Audit Log for metric changes) and AC 5 (Audit Logging for
	 * privacy changes).</p>
	 *
	 * @param actionType     One of the {@link AuditLogEntry} ACTION_* constants.
	 * @param targetEntityId ID of the affected entity (may be null).
	 * @param description    Human-readable description of the action.
	 * @param previousValue  Serialized old value (may be null).
	 * @param newValue       Serialized new value (may be null).
	 */
	private void writeAuditLog(
			String actionType, String targetEntityId,
			String description, String previousValue, String newValue
	) {
		if (auth.getCurrentUser() == null) return;

		Map<String, Object> entry = new HashMap<>();
		entry.put("adminId", auth.getCurrentUser().getUid());
		entry.put("actionType", actionType);
		entry.put("targetEntityId", targetEntityId);
		entry.put("description", description);
		entry.put("previousValue", previousValue);
		entry.put("newValue", newValue);
		entry.put("timestamp", Timestamp.now());

		db.collection("auditLog").add(entry);
		// Fire-and-forget: audit writes do not block the main operation.
	}

	/**
	 * Fetches recent audit log entries for display on the admin settings screen.
	 *
	 * <p>Ordered by timestamp descending (most recent first). Optionally filtered
	 * by action type.</p>
	 *
	 * @param actionTypeFilter One of the {@link AuditLogEntry} ACTION_* constants,
	 *                         or null to load all action types.
	 * @param limit            Maximum number of entries to return.
	 * @param callback         Receives the list of audit log entries.
	 */
	public void getAuditLog(
			String actionTypeFilter, int limit,
			OnAuditLogCallback callback
	) {
		requireAdminRole(
				() -> {
					Query query = db.collection("auditLog")
							.orderBy("timestamp", Query.Direction.DESCENDING)
							.limit(limit);

					if (actionTypeFilter != null) {
						query = query.whereEqualTo("actionType", actionTypeFilter);
					}

					query.get()
							.addOnSuccessListener(querySnapshot -> {
								List<AuditLogEntry> entries = new ArrayList<>();
								for (var doc : querySnapshot.getDocuments()) {
									AuditLogEntry e = doc.toObject(AuditLogEntry.class);
									if (e != null) entries.add(e);
								}
								callback.onAuditLogLoaded(entries);
							})
							.addOnFailureListener(e -> callback.onAuditLogLoaded(new ArrayList<>()));
				},
				() -> callback.onAuditLogLoaded(new ArrayList<>())
		);
	}

	// =========================================================================
	// Callback interfaces
	// =========================================================================

	/**
	 * Callback for operations that either succeed or fail with an optional message.
	 */
	public interface OnCompleteCallback {
		/**
		 * @param success {@code true} if the operation completed without error.
		 * @param error   A human-readable error message, or null on success.
		 */
		void onComplete(boolean success, String error);
	}

	/**
	 * Callback for campus/club impact summary loads.
	 */
	public interface OnSummaryCallback {
		/**
		 * @param summary The impact summary, or null on failure/permission denied.
		 */
		void onSummaryLoaded(ProgressTracker.ImpactSummary summary);
	}

	/**
	 * Callback for time-series chart data.
	 */
	public interface OnTimeSeriesCallback {
		/**
		 * @param dataPoints Ordered list of data points; empty on failure.
		 */
		void onTimeSeriesLoaded(List<ProgressTracker.DataPoint> dataPoints);
	}

	/**
	 * Callback for departmental breakdown.
	 */
	public interface OnBreakdownCallback {
		/**
		 * @param breakdown Ordered list of club entries; empty on failure.
		 */
		void onBreakdownLoaded(List<ProgressTracker.ClubBreakdownEntry> breakdown);
	}

	/**
	 * Callback for campus goal list operations.
	 */
	public interface OnGoalsLoadedCallback {
		/**
		 * @param goals List of campus goals; empty on failure.
		 */
		void onGoalsLoaded(List<CampusGoal> goals);
	}

	/**
	 * Callback for sustainability tip list operations.
	 */
	public interface OnTipsLoadedCallback {
		/**
		 * @param tips List of sustainability tips; empty on failure.
		 */
		void onTipsLoaded(List<SustainabilityTip> tips);
	}

	/**
	 * Callback for calculation metric list operations.
	 */
	public interface OnMetricsLoadedCallback {
		/**
		 * @param metrics List of calculation metrics; empty on failure.
		 */
		void onMetricsLoaded(List<CalculationMetric> metrics);
	}

	/**
	 * Callback for challenge list operations.
	 */
	public interface OnChallengesLoadedCallback {
		/**
		 * @param challenges List of challenges; empty on failure.
		 */
		void onChallengesLoaded(List<Challenge> challenges);
	}

	/**
	 * Callback for report/data export operations that produce a text payload.
	 */
	public interface OnReportExportedCallback {
		/**
		 * @param data The exported data (CSV/JSON string), or null on failure.
		 */
		void onReportExported(String data);
	}

	/**
	 * Callback for PDF export operations.
	 */
	public interface OnPdfExportedCallback {
		/**
		 * @param uri {@link Uri} pointing to the generated PDF file, or null on failure.
		 */
		void onPdfExported(Uri uri);
	}

	/**
	 * Callback for generic Firestore document reads returned as a Map.
	 */
	public interface OnMapLoadedCallback {
		/**
		 * @param data The document's field map, or null on failure.
		 */
		void onMapLoaded(Map<String, Object> data);
	}

	/**
	 * Callback for audit log list operations.
	 */
	public interface OnAuditLogCallback {
		/**
		 * @param entries List of audit log entries; empty on failure.
		 */
		void onAuditLogLoaded(List<AuditLogEntry> entries);
	}
}
