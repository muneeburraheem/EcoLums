package com.example.ecolums;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bottom sheet dialog for logging a new sustainability activity.
 * Handles Transport, Energy, and Waste categories.
 * Computes CO₂ and points using metrics fetched from Firestore,
 * then saves the log and updates the user's totalPoints.
 */
public class AddActivityBottomSheet extends BottomSheetDialogFragment {

	public interface OnActivityLoggedListener {
		void onLogged();
	}

	private OnActivityLoggedListener listener;

	public void setOnActivityLoggedListener(OnActivityLoggedListener l) {
		this.listener = l;
	}

	private RadioGroup rgCategory;
	private LinearLayout llTransport, llEnergy, llWaste;
	private Spinner spinnerTransportMode, spinnerWasteType;
	private TextInputEditText etDistanceKm, etEnergyKwh, etWasteKg;
	private MaterialButton btnSubmit;
	private TextView tvMlStatus, tvMlDot;
	private FirebaseFirestore db;

	// Cached metrics: key → factor value
	private final Map<String, Double> metricFactors = new HashMap<>();

	@Nullable
	@Override
	public View onCreateView(
			@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState
	) {
		View view = inflater.inflate(R.layout.bottom_sheet_add_activity, container, false);
		db = FirebaseFirestore.getInstance();

		rgCategory = view.findViewById(R.id.rg_category);
		llTransport = view.findViewById(R.id.ll_transport_fields);
		llEnergy = view.findViewById(R.id.ll_energy_fields);
		llWaste = view.findViewById(R.id.ll_waste_fields);
		spinnerTransportMode = view.findViewById(R.id.spinner_transport_mode);
		spinnerWasteType = view.findViewById(R.id.spinner_waste_type);
		etDistanceKm = view.findViewById(R.id.et_distance_km);
		etEnergyKwh = view.findViewById(R.id.et_energy_kwh);
		etWasteKg = view.findViewById(R.id.et_waste_kg);
		btnSubmit = view.findViewById(R.id.btn_submit_log);
		tvMlStatus = view.findViewById(R.id.tv_ml_status);
		tvMlDot = view.findViewById(R.id.tv_ml_dot);
		updateMlStatusIndicator();

		// Populate spinners
		ArrayAdapter<String> transportAdapter = new ArrayAdapter<>(
				requireContext(),
				android.R.layout.simple_spinner_item,
				new String[] { "Bus", "Bike", "Walk", "Train", "Car" }
		);
		transportAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerTransportMode.setAdapter(transportAdapter);

		ArrayAdapter<String> wasteAdapter = new ArrayAdapter<>(
				requireContext(),
				android.R.layout.simple_spinner_item,
				new String[] { "Recycle", "Compost", "Landfill" }
		);
		wasteAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerWasteType.setAdapter(wasteAdapter);

		// Show/hide fields based on category selection
		rgCategory.setOnCheckedChangeListener((group, checkedId) -> {
			llTransport.setVisibility(checkedId == R.id.rb_transport ? View.VISIBLE : View.GONE);
			llEnergy.setVisibility(checkedId == R.id.rb_energy ? View.VISIBLE : View.GONE);
			llWaste.setVisibility(checkedId == R.id.rb_waste ? View.VISIBLE : View.GONE);
		});

		loadMetricFactors();
		btnSubmit.setOnClickListener(v -> submitLog());
		return view;
	}

	/** Updates the AI model status chip shown at the top of the form. */
	private void updateMlStatusIndicator() {
		Co2Estimator estimator = EcoLumsApp.getCo2Estimator();
		boolean ready = estimator != null && estimator.isAvailable();
		if (tvMlStatus == null || tvMlDot == null) return;
		if (ready) {
			tvMlDot.setTextColor(Color.parseColor("#4CAF50"));
			tvMlStatus.setText("AI Estimator: Ready (TFLite · R²=0.92)");
		} else {
			tvMlDot.setTextColor(Color.parseColor("#FFC107"));
			tvMlStatus.setText("AI Estimator: Unavailable — formula fallback active");
		}
	}

	/**
	 * Pre-fetches all calculation metric factors so we don't need a round-trip per submit.
	 */
	private void loadMetricFactors() {
		db.collection("calculationMetrics").get()
				.addOnSuccessListener(querySnapshot -> {
					for (var doc : querySnapshot.getDocuments()) {
						String key = doc.getString("metricKey");
						Double val = doc.getDouble("value");
						if (key != null && val != null) metricFactors.put(key, val);
					}
				});
	}

	private void submitLog() {
		User user = UserSession.getInstance().getCurrentUser();
		if (user == null) return;

		int checkedId = rgCategory.getCheckedRadioButtonId();
		ActivityLog log = new ActivityLog(user.getUserId(), Timestamp.now(), "");

		try {
			if (checkedId == R.id.rb_transport) {
				buildTransportLog(log);
			} else if (checkedId == R.id.rb_energy) {
				buildEnergyLog(log);
			} else {
				buildWasteLog(log);
			}
		} catch (NumberFormatException e) {
			Toast.makeText(getContext(), "Please enter a valid number.", Toast.LENGTH_SHORT).show();
			return;
		}

		// Compute CO₂ via static factors (used as FL ground-truth label)
		log.convertToCO2(buildMetricMap());
		float staticCo2 = (float) log.getCo2EquivalentKg();
		float mlCo2 = staticCo2; // default to static if ML fails

		try {
			// Re-estimate with ML model (applies FL global correction on top)
			log.convertToCO2WithML(buildMetricMap());
			mlCo2 = (float) log.getCo2EquivalentKg();

			// Record (mlOutput, staticLabel) pair for federated learning personalisation
			FederatedLearningManager fl = EcoLumsApp.getFlManager();
			if (fl != null) {
				fl.recordExample(mlCo2, staticCo2);
				fl.maybeUpload(user.getUserId());
			}
		} catch (Exception e) {
			android.util.Log.e("AddActivity", "ML estimation failed, using static formula", e);
			log.convertToCO2(buildMetricMap());
			mlCo2 = staticCo2;
		}

		// Anomaly detection — flag suspicious entries (extreme quantities, bot patterns)
		try {
			AnomalyDetector detector = EcoLumsApp.getAnomalyDetector();
			if (detector != null && detector.isAvailable()) {
				int   subType   = log.resolveSubTypePublic();
				float quantity  = (float) (log.getDistanceKm() + log.getEnergyKwh() + log.getWasteKg());
				java.util.Calendar cal = java.util.Calendar.getInstance();
				int   hour      = cal.get(java.util.Calendar.HOUR_OF_DAY);
				int   dow       = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1;
				int   month     = cal.get(java.util.Calendar.MONTH) + 1;
				float isWeekend = (dow == 0 || dow == 6) ? 1.0f : 0.0f;

				float score    = detector.score(subType, quantity, hour, dow, month, isWeekend);
				if (!Float.isNaN(score)) {
					boolean flagged = detector.isAnomalous(score);
					log.setAnomalyScore(score);
					log.setFlagged(flagged);
					if (flagged && isAdded() && getContext() != null) {
						Toast.makeText(getContext(),
								"Activity flagged for review (unusual pattern detected).",
								Toast.LENGTH_LONG).show();
					}
				}
			}
		} catch (Exception e) {
			android.util.Log.e("AddActivity", "Anomaly detection failed", e);
		}

		// Show AI comparison dialog before saving
		if (!isAdded() || getContext() == null) return;
		showAiConfirmationDialog(log, staticCo2, mlCo2, user);
	}

	/**
	 * Shows a dialog comparing the static formula CO₂ vs the on-device AI model estimate,
	 * letting the user confirm before the activity is persisted to Firestore.
	 */
	private void showAiConfirmationDialog(ActivityLog log, float staticCo2, float mlCo2, User user) {
		Co2Estimator estimator = EcoLumsApp.getCo2Estimator();
		boolean mlActive = estimator != null && estimator.isAvailable() && Float.isFinite(mlCo2);
		boolean metricsLoaded = !metricFactors.isEmpty();

		StringBuilder msg = new StringBuilder();

		// AI model section
		if (mlActive) {
			msg.append("AI Model (TFLite)   ✓ Active\n");
			msg.append(String.format("  Estimate:  %.3f kg CO₂\n\n", mlCo2));
		} else {
			msg.append("AI Model   ✗ Unavailable — using formula\n\n");
		}

		// Formula section
		if (metricsLoaded) {
			msg.append(String.format("Formula estimate:  %.3f kg CO₂\n", staticCo2));
		} else {
			msg.append("Formula estimate:  — (metrics loading)\n");
		}

		// Delta
		if (mlActive && metricsLoaded && staticCo2 != 0) {
			float deltaPct = ((mlCo2 - staticCo2) / Math.abs(staticCo2)) * 100f;
			msg.append(String.format("Delta vs formula:  %+.1f%%\n", deltaPct));
		}

		msg.append(String.format("\nPoints to earn:  %.0f pts", log.getPointsEarned()));

		AnomalyDetector det = EcoLumsApp.getAnomalyDetector();
		if (det != null && det.isAvailable()) {
			msg.append(String.format("\nAnomaly score:   %.4f", log.getAnomalyScore()));
			if (log.isFlagged()) msg.append("  ⚠ flagged");
		}

		new AlertDialog.Builder(requireContext())
				.setTitle("AI CO₂ Estimation")
				.setMessage(msg.toString())
				.setPositiveButton("Save Activity", (d, w) -> persistLog(log, user))
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void persistLog(ActivityLog log, User user) {
		// Hardcoded admin has no Firestore user doc — skip persistence, just show feedback
		if ("admin_hardcoded".equals(user.getUserId())) {
			user.setTotalPoints(user.getTotalPoints() + log.getPointsEarned());
			if (isAdded() && getContext() != null) {
				Toast.makeText(
						getContext(),
						String.format("Saved! +%.1f kg CO₂  •  +%.0f pts",
								log.getCo2EquivalentKg(), log.getPointsEarned()),
						Toast.LENGTH_SHORT
				).show();
			}
			if (listener != null) listener.onLogged();
			dismiss();
			return;
		}

		// Save to Firestore
		Map<String, Object> logData = new HashMap<>();
		logData.put("userId", log.getUserId());
		logData.put("date", log.getDate());
		logData.put("category", log.getCategory());
		logData.put("transportMode", log.getTransportMode());
		logData.put("distanceKm", log.getDistanceKm());
		logData.put("energyKwh", log.getEnergyKwh());
		logData.put("wasteType", log.getWasteType());
		logData.put("wasteKg", log.getWasteKg());
		logData.put("co2EquivalentKg", Double.isFinite(log.getCo2EquivalentKg()) ? log.getCo2EquivalentKg() : 0.0);
		logData.put("pointsEarned", Double.isFinite(log.getPointsEarned()) ? log.getPointsEarned() : 0.0);
		logData.put("anomalyScore", log.getAnomalyScore());
		logData.put("isFlagged", log.isFlagged());

		db.collection("users").document(user.getUserId())
				.collection("activityLogs")
				.add(logData)
				.addOnSuccessListener(ref -> {
					if (!isAdded()) return; // fragment detached — skip UI ops

					// Update running total on user document
					double newPoints = user.getTotalPoints() + log.getPointsEarned();
					user.setTotalPoints(newPoints);
					db.collection("users").document(user.getUserId())
							.update("totalPoints", newPoints);

					// Update streak
					StreakManager.updateStreak(user);

					// Check badge milestones
					ProgressTracker tracker = new ProgressTracker();
					tracker.checkMilestonesForUser(user, newlyEarned -> {});
					tracker.checkFirstActivityBadge(user);
					if (log.getTransportMode() != null) {
						tracker.checkActivityCountBadges(user, log.getTransportMode());
					}

					// Update progress for ALL active campus goals (AC 2 & AC 4 — Campus Goals story)
					updateAllActiveCampusGoals(log);

					// Update progress in any active, enrolled challenges matching this category
					updateChallengeProgress(log, user.getUserId());

					if (getContext() != null) {
						Toast.makeText(
								getContext(),
								String.format(
										"Saved! +%.1f CO₂ • +%.0f pts",
										log.getCo2EquivalentKg(), log.getPointsEarned()
								),
								Toast.LENGTH_SHORT
						).show();
					}
					if (listener != null) listener.onLogged();
					dismiss();
				})
				.addOnFailureListener(e -> {
					if (isAdded() && getContext() != null) {
						Toast.makeText(
								getContext(), "Save failed: " + e.getMessage(),
								Toast.LENGTH_SHORT
						).show();
					}
				});
	}

	private void buildTransportLog(ActivityLog log) {
		log.setCategory(ActivityLog.CATEGORY_TRANSPORT);
		String[] modes = { "BUS", "BIKE", "WALK", "TRAIN", "CAR" };
		log.setTransportMode(modes[spinnerTransportMode.getSelectedItemPosition()]);
		String raw = etDistanceKm.getText() != null ? etDistanceKm.getText().toString() : "0";
		log.setDistanceKm(Double.parseDouble(raw.isEmpty() ? "0" : raw));
	}

	private void buildEnergyLog(ActivityLog log) {
		log.setCategory(ActivityLog.CATEGORY_ENERGY);
		String raw = etEnergyKwh.getText() != null ? etEnergyKwh.getText().toString() : "0";
		log.setEnergyKwh(Double.parseDouble(raw.isEmpty() ? "0" : raw));
	}

	private void buildWasteLog(ActivityLog log) {
		log.setCategory(ActivityLog.CATEGORY_WASTE);
		String[] types = { "RECYCLE", "COMPOST", "LANDFILL" };
		log.setWasteType(types[spinnerWasteType.getSelectedItemPosition()]);
		String raw = etWasteKg.getText() != null ? etWasteKg.getText().toString() : "0";
		log.setWasteKg(Double.parseDouble(raw.isEmpty() ? "0" : raw));
	}

	/**
	 * Finds active challenges the user has joined whose goalType matches the logged
	 * activity category, and increments their participantScore accordingly.
	 * Transport → distanceKm, Energy → energyKwh, Waste → wasteKg, Overall → pointsEarned.
	 */
	private void updateChallengeProgress(ActivityLog log, String userId) {
		String category = log.getCategory();
		final String matchGoalType;
		final double metricValue;

		if (ActivityLog.CATEGORY_TRANSPORT.equals(category)) {
			matchGoalType = Challenge.GOAL_TYPE_TRANSPORT;
			metricValue = log.getDistanceKm();
		} else if (ActivityLog.CATEGORY_ENERGY.equals(category)) {
			matchGoalType = Challenge.GOAL_TYPE_ENERGY;
			metricValue = log.getEnergyKwh();
		} else if (ActivityLog.CATEGORY_WASTE.equals(category)) {
			matchGoalType = Challenge.GOAL_TYPE_WASTE;
			metricValue = log.getWasteKg();
		} else {
			return;
		}

		db.collection("challenges")
				.whereEqualTo("status", Challenge.STATUS_ACTIVE)
				.whereArrayContains("participantIds", userId)
				.get()
				.addOnSuccessListener(snapshot -> {
					for (var doc : snapshot.getDocuments()) {
						Challenge c = doc.toObject(Challenge.class);
						if (c == null) continue;
						String gt = c.getGoalType();
						boolean matches = matchGoalType.equals(gt)
								|| Challenge.GOAL_TYPE_OVERALL.equals(gt);
						if (!matches) continue;

						double increment = Challenge.GOAL_TYPE_OVERALL.equals(gt)
								? log.getPointsEarned() : metricValue;

						// Estimate updated score for notification threshold checks
						double current = 0;
						if (c.getParticipantScores() != null
								&& c.getParticipantScores().containsKey(userId)) {
							Double val = c.getParticipantScores().get(userId);
							if (val != null) current = val;
						}
						final double estimatedScore = current + increment;

						// Use atomic increment to avoid race conditions on concurrent logs
						db.collection("challenges").document(doc.getId())
								.update("participantScores." + userId, FieldValue.increment(increment))
								.addOnSuccessListener(v -> {
									sendChallengeProgressNotifications(doc.getId(), c, userId, estimatedScore);
									if (estimatedScore >= c.getTargetValue() && c.getRewardBadgeId() != null) {
										awardChallengeBadge(userId, c.getRewardBadgeId());
									}
								});
					}
				});
	}

	private void sendChallengeProgressNotifications(
			String challengeId, Challenge challenge, String userId, double updated
	) {
		if (challenge.getNotificationMilestones() == null || challenge.getTargetValue() <= 0) return;
		double pct = (updated / challenge.getTargetValue()) * 100.0;
		for (Integer milestone : challenge.getNotificationMilestones()) {
			if (milestone == null || pct < milestone) continue;
			Map<String, Object> notification = new HashMap<>();
			notification.put("type", "CHALLENGE_PROGRESS");
			notification.put("challengeId", challengeId);
			notification.put("title", milestone >= 100 ? "Challenge goal reached!" : milestone + "% challenge progress");
			notification.put("body", challenge.getTitle());
			notification.put("timestamp", Timestamp.now());
			db.collection("users").document(userId)
					.collection("notifications")
					.document(challengeId + "_" + milestone)
					.set(notification);
		}
	}

	/**
	 * Updates progress for every ACTIVE campus goal whose metricType matches the
	 * logged activity, and fires milestone celebration notifications (AC 2, AC 4
	 * of the Campus Goals user story).
	 *
	 * <p>CO2-type goals receive the full co2EquivalentKg value regardless of category.
	 * WASTE-type goals receive the wasteKg value (waste logs only).
	 * ENERGY-type goals receive the energyKwh value (energy logs only).</p>
	 */
	private void updateAllActiveCampusGoals(ActivityLog log) {
		db.collection("campusGoals")
				.whereEqualTo("status", CampusGoal.STATUS_ACTIVE)
				.get()
				.addOnSuccessListener(snapshot -> {
					for (var doc : snapshot.getDocuments()) {
						CampusGoal goal = doc.toObject(CampusGoal.class);
						if (goal == null) continue;
						goal.setGoalId(doc.getId());

						// Determine contribution for this goal's metric type
						double contribution = 0;
						String metric = goal.getMetricType();
						if (CampusGoal.METRIC_CO2.equals(metric)) {
							contribution = log.getCo2EquivalentKg();
						} else if (CampusGoal.METRIC_WASTE.equals(metric)
								&& ActivityLog.CATEGORY_WASTE.equals(log.getCategory())) {
							contribution = log.getWasteKg();
						} else if (CampusGoal.METRIC_ENERGY.equals(metric)
								&& ActivityLog.CATEGORY_ENERGY.equals(log.getCategory())) {
							contribution = log.getEnergyKwh();
						}
						if (contribution <= 0) continue;

						final double delta = contribution;
						final double prevProgress = goal.getCurrentProgress();
						final double newProgress = prevProgress + delta;

						db.collection("campusGoals").document(doc.getId())
								.update("currentProgress", newProgress)
								.addOnSuccessListener(v ->
										checkCampusGoalMilestones(doc.getId(), goal, prevProgress, newProgress));
					}
				});
	}

	/**
	 * Sends a broadcast notification when a campus goal crosses a milestone percentage
	 * (25%, 50%, 75%, 100%). Written to broadcastNotifications and to the current
	 * user's personal notifications — satisfying AC 4 of the Campus Goals story.
	 */
	private void checkCampusGoalMilestones(String goalId, CampusGoal goal,
	                                        double prev, double current) {
		if (goal.getTargetValue() <= 0) return;
		List<Integer> milestones = goal.getNotificationMilestones();
		if (milestones == null || milestones.isEmpty()) return;

		double prevPct = (prev / goal.getTargetValue()) * 100.0;
		double newPct  = (current / goal.getTargetValue()) * 100.0;

		for (Integer milestone : milestones) {
			if (milestone == null) continue;
			// Crossed from below to at-or-above this milestone
			if (prevPct < milestone && newPct >= milestone) {
				String title = milestone >= 100
						? "🎉 Campus Goal Achieved!"
						: "🌱 Campus Goal: " + milestone + "% reached!";
				String body  = goal.getTitle() + " — " + milestone + "% complete";

				// Broadcast to all (picked up by Cloud Function for push notifications)
				Map<String, Object> broadcast = new HashMap<>();
				broadcast.put("type", "CAMPUS_GOAL_MILESTONE");
				broadcast.put("goalId", goalId);
				broadcast.put("milestone", milestone);
				broadcast.put("title", title);
				broadcast.put("body", body);
				broadcast.put("timestamp", Timestamp.now());
				db.collection("broadcastNotifications").add(broadcast);

				// Personal in-app notification for the triggering user
				User u = UserSession.getInstance().getCurrentUser();
				if (u != null) {
					Map<String, Object> personal = new HashMap<>();
					personal.put("type", "CAMPUS_GOAL_MILESTONE");
					personal.put("title", title);
					personal.put("body", body);
					personal.put("timestamp", Timestamp.now());
					db.collection("users").document(u.getUserId())
							.collection("notifications")
							.document(goalId + "_" + milestone)
							.set(personal);
				}
			}
		}
	}

	private void awardChallengeBadge(String userId, String badgeId) {
		db.collection("badges").document(badgeId).get()
				.addOnSuccessListener(badgeDoc -> {
					Badge badge = badgeDoc.toObject(Badge.class);
					if (badge == null) return;
					if (badge.getBadgeId() == null) badge.setBadgeId(badgeDoc.getId());
					User user = new User();
					user.loadProfile(userId, success -> {
						if (success) user.awardBadge(badge, ignored -> {});
					});
				});
	}

	/**
	 * Converts the flat metricFactors map into the Map<String, CalculationMetric> that
	 * ActivityLog.convertToCO2 expects.
	 */
	private Map<String, CalculationMetric> buildMetricMap() {
		Map<String, CalculationMetric> map = new HashMap<>();
		for (Map.Entry<String, Double> entry : metricFactors.entrySet()) {
			CalculationMetric m = new CalculationMetric();
			m.setMetricKey(entry.getKey());
			m.setValue(entry.getValue());
			map.put(entry.getKey(), m);
		}
		return map;
	}
}
