package com.example.ecolums;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

/**
 * Home screen fragment. Shows:
 * - Eco Score (total points)
 * - Quick links to Leaderboard and Campus Impact
 * - Today's Activities list
 * - FAB to add a new activity
 */
public class HomeFragment extends Fragment {

	private TextView tvEcoPoints, tvRank, tvNoActivities, tvNoChallenges, tvStreak;
	private LinearLayout llTodayActivities, llActiveChallenges;
	private CardView cardLeaderboard, cardCampusImpact, cardTips, cardTrophyCase, cardMerchStore;
	private FirebaseFirestore db;

	@Nullable
	@Override
	public View onCreateView(
			@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState
	) {
		View view = inflater.inflate(R.layout.fragment_home, container, false);

		db = FirebaseFirestore.getInstance();

		tvEcoPoints = view.findViewById(R.id.tv_eco_points);
		tvStreak = view.findViewById(R.id.tv_streak);
		tvNoActivities = view.findViewById(R.id.tv_no_activities);
		tvNoChallenges = view.findViewById(R.id.tv_no_challenges);
		llTodayActivities = view.findViewById(R.id.ll_today_activities);
		llActiveChallenges = view.findViewById(R.id.ll_active_challenges);
		cardLeaderboard = view.findViewById(R.id.card_leaderboard);
		cardCampusImpact = view.findViewById(R.id.card_campus_impact);
		cardTips = view.findViewById(R.id.card_tips);
		cardTrophyCase = view.findViewById(R.id.card_trophy_case);
		cardMerchStore = view.findViewById(R.id.card_merch_store);

		cardLeaderboard.setOnClickListener(v ->
				startActivity(new Intent(getActivity(), LeaderboardActivity.class)));

		cardCampusImpact.setOnClickListener(v ->
				startActivity(new Intent(getActivity(), CampusImpactActivity.class)));

		cardTips.setOnClickListener(v ->
				startActivity(new Intent(getActivity(), SustainabilityTipsActivity.class)));

		if (cardTrophyCase != null) {
			cardTrophyCase.setOnClickListener(v ->
					startActivity(new Intent(getActivity(), TrophyCaseActivity.class)));
		}
		if (cardMerchStore != null) {
			cardMerchStore.setOnClickListener(v ->
					startActivity(new Intent(getActivity(), MerchStoreActivity.class)));
		}

		// "View All" navigates to the Challenges tab via the bottom nav
		view.findViewById(R.id.tv_view_all_challenges).setOnClickListener(v -> {
			if (getActivity() instanceof MainActivity) {
				((MainActivity) getActivity()).navigateToChallenges();
			}
		});

		view.findViewById(R.id.fab_add_activity).setOnClickListener(v -> openAddActivity());

		loadUserPoints();
		loadActiveChallenges();
		loadTodaysLogs();

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
		loadUserPoints();
	}

	/**
	 * Reads totalPoints from the UserSession (already loaded at login).
	 */
	private void loadUserPoints() {
		User user = UserSession.getInstance().getCurrentUser();
		if (user != null) {
			tvEcoPoints.setText(String.format(
					Locale.getDefault(),
					"%.0f Points", user.getTotalPoints()
			));
			int streak = user.getCurrentStreak();
			String streakLabel = streak == 1 ? "day" : "days";
			tvStreak.setText("🔥 " + streak + " " + streakLabel + " streak");
		}
	}

	/**
	 * Loads up to 3 challenges the current user has joined.
	 */
	private void loadActiveChallenges() {
		User user = UserSession.getInstance().getCurrentUser();
		if (user == null) {
			tvNoChallenges.setVisibility(View.VISIBLE);
			return;
		}
		db.collection("challenges")
				.whereEqualTo("status", Challenge.STATUS_ACTIVE)
				.whereArrayContains("participantIds", user.getUserId())
				.limit(3)
				.get()
				.addOnSuccessListener(querySnapshot -> {
					if (!isAdded()) return;
					if (querySnapshot.isEmpty()) {
						tvNoChallenges.setVisibility(View.VISIBLE);
						return;
					}
					tvNoChallenges.setVisibility(View.GONE);
					llActiveChallenges.removeAllViews();

					for (var doc : querySnapshot.getDocuments()) {
						Challenge c = doc.toObject(Challenge.class);
						if (c == null) continue;
						c.setChallengeId(doc.getId());
						View row = inflateChallengeRow(c);
						llActiveChallenges.addView(row);
					}
				})
				.addOnFailureListener(e -> {
					if (isAdded()) tvNoChallenges.setVisibility(View.VISIBLE);
				});
	}

	/**
	 * Inflates a compact challenge row for the Home screen with real progress.
	 */
	private View inflateChallengeRow(Challenge c) {
		View row = LayoutInflater.from(getContext())
				.inflate(R.layout.item_challenge, llActiveChallenges, false);

		TextView tvIcon = row.findViewById(R.id.tv_challenge_icon);
		TextView tvTitle = row.findViewById(R.id.tv_challenge_title);
		TextView tvDescription = row.findViewById(R.id.tv_challenge_description);
		TextView tvProgress = row.findViewById(R.id.tv_challenge_progress);
		ProgressBar pb = row.findViewById(R.id.pb_challenge_progress);

		// Icon by goal type
		if (c.getGoalType() != null) {
			switch (c.getGoalType()) {
			case Challenge.GOAL_TYPE_TRANSPORT:
				tvIcon.setText("🚴");
				break;
			case Challenge.GOAL_TYPE_ENERGY:
				tvIcon.setText("⚡");
				break;
			case Challenge.GOAL_TYPE_WASTE:
				tvIcon.setText("♻");
				break;
			default:
				tvIcon.setText("🌍");
				break;
			}
		}
		tvTitle.setText(c.getTitle() != null ? c.getTitle() : "Challenge");

		// Description
		if (c.getDescription() != null && !c.getDescription().isEmpty()) {
			tvDescription.setText(c.getDescription());
			tvDescription.setVisibility(View.VISIBLE);
		} else {
			tvDescription.setVisibility(View.GONE);
		}

		// Real progress from participantScores
		double totalScore = 0;
		Map<String, Double> scores = c.getParticipantScores();
		if (scores != null) {
			for (double s : scores.values()) totalScore += s;
		}
		double target = c.getTargetValue();
		int pct = (target > 0) ? (int) Math.min(100, (totalScore / target) * 100) : 0;
		pb.setProgress(pct);
		String unit = c.getTargetUnit() != null ? c.getTargetUnit() : "";
		tvProgress.setText(String.format(
				Locale.getDefault(),
				"%.1f / %.0f %s",
				totalScore,
				target,
				unit
		));

		// Hide the Join button (user is already enrolled on the home screen)
		row.findViewById(R.id.btn_join_challenge).setVisibility(View.GONE);

		// Tap → detail screen
		row.setOnClickListener(v -> {
			Intent intent = new Intent(getContext(), ChallengeDetailActivity.class);
			intent.putExtra(ChallengeDetailActivity.EXTRA_CHALLENGE_ID, c.getChallengeId());
			startActivity(intent);
		});

		return row;
	}

	/**
	 * Queries today's activity logs and inflates them into the LinearLayout.
	 */
	private void loadTodaysLogs() {
		User user = UserSession.getInstance().getCurrentUser();
		if (user == null) return;

		// Build start-of-day timestamp
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		Timestamp startOfDay = new Timestamp(cal.getTimeInMillis() / 1000, 0);

		db.collection("users")
				.document(user.getUserId())
				.collection("activityLogs")
				.whereGreaterThanOrEqualTo("date", startOfDay)
				.orderBy("date", Query.Direction.DESCENDING)
				.get()
				.addOnSuccessListener(querySnapshot -> {
					if (!isAdded()) return;
					if (querySnapshot.isEmpty()) {
						tvNoActivities.setVisibility(View.VISIBLE);
						return;
					}
					tvNoActivities.setVisibility(View.GONE);
					llTodayActivities.removeAllViews();

					for (var doc : querySnapshot.getDocuments()) {
						ActivityLog log = doc.toObject(ActivityLog.class);
						if (log == null) continue;
						View row = inflateActivityRow(log);
						llTodayActivities.addView(row);
					}
				})
				.addOnFailureListener(e -> {
					if (isAdded()) tvNoActivities.setVisibility(View.VISIBLE);
				});
	}

	/**
	 * Inflates a single activity row view inline (no RecyclerView needed for today).
	 */
	private View inflateActivityRow(ActivityLog log) {
		View row = LayoutInflater.from(getContext())
				.inflate(R.layout.item_activity_log, llTodayActivities, false);

		TextView tvIcon = row.findViewById(R.id.tv_category_icon);
		TextView tvTitle = row.findViewById(R.id.tv_log_title);
		TextView tvDetail = row.findViewById(R.id.tv_log_detail);
		TextView tvCo2 = row.findViewById(R.id.tv_co2_saved);
		TextView tvDate = row.findViewById(R.id.tv_log_date);

		tvDate.setText("Today");

		switch (log.getCategory()) {
		case ActivityLog.CATEGORY_TRANSPORT:
			tvIcon.setText("🚌");
			tvTitle.setText(formatTransportTitle(log.getTransportMode()));
			tvDetail.setText(String.format(Locale.getDefault(), "%.1f km", log.getDistanceKm()));
			break;
		case ActivityLog.CATEGORY_ENERGY:
			tvIcon.setText("⚡");
			tvTitle.setText("Energy Saved");
			tvDetail.setText(String.format(Locale.getDefault(), "%.1f kWh", log.getEnergyKwh()));
			break;
		case ActivityLog.CATEGORY_WASTE:
			tvIcon.setText("♻");
			tvTitle.setText(formatWasteTitle(log.getWasteType()));
			tvDetail.setText(String.format(Locale.getDefault(), "%.1f kg", log.getWasteKg()));
			break;
		}

		tvCo2.setText(String.format(Locale.getDefault(), "+%.1f", log.getCo2EquivalentKg()));
		return row;
	}

	private String formatTransportTitle(String mode) {
		if (mode == null) return "Transport";
		switch (mode) {
		case ActivityLog.TRANSPORT_BUS:
			return "Used Public Transport";
		case ActivityLog.TRANSPORT_BIKE:
			return "Bike Ride";
		case ActivityLog.TRANSPORT_WALK:
			return "Walked";
		case ActivityLog.TRANSPORT_TRAIN:
			return "Train Journey";
		default:
			return "Transport";
		}
	}

	private String formatWasteTitle(String type) {
		if (type == null) return "Waste";
		switch (type) {
		case ActivityLog.WASTE_RECYCLE:
			return "Recycled Waste";
		case ActivityLog.WASTE_COMPOST:
			return "Composted Waste";
		default:
			return "Landfill Waste";
		}
	}

	private void openAddActivity() {
		AddActivityBottomSheet sheet = new AddActivityBottomSheet();
		sheet.setOnActivityLoggedListener(() -> {
			loadUserPoints();
			loadTodaysLogs();
			loadActiveChallenges();
		});
		sheet.show(getParentFragmentManager(), "AddActivity");
	}
}
