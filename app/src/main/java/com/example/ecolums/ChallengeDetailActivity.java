package com.example.ecolums;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full-screen detail view for a single challenge.
 * Launched by tapping a challenge card on Home or the Challenges tab.
 * <p>
 * Extras required: {@link #EXTRA_CHALLENGE_ID} (String)
 */
public class ChallengeDetailActivity extends AppCompatActivity {

	public static final String EXTRA_CHALLENGE_ID = "challenge_id";

	private TextView tvTitle, tvCategory, tvIcon, tvDescription;
	private TextView tvProgressText, tvPct, tvTarget, tvParticipants, tvMyProgress;
	private ProgressBar pbProgress;
	private CardView cardMyProgress, cardTeamLeaderboard;
	private LinearLayout llTeamLeaderboard;
	private MaterialButton btnJoinLeave;
	private FirebaseFirestore db;
	private Challenge loadedChallenge;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_challenge_detail);

		db = FirebaseFirestore.getInstance();

		// Back navigation
		findViewById(R.id.tv_back).setOnClickListener(v -> finish());

		tvTitle = findViewById(R.id.tv_detail_title);
		tvCategory = findViewById(R.id.tv_detail_category);
		tvIcon = findViewById(R.id.tv_challenge_icon_header);
		tvDescription = findViewById(R.id.tv_detail_description);
		tvProgressText = findViewById(R.id.tv_detail_progress_text);
		tvPct = findViewById(R.id.tv_detail_pct);
		tvTarget = findViewById(R.id.tv_detail_target);
		tvParticipants = findViewById(R.id.tv_detail_participants);
		tvMyProgress = findViewById(R.id.tv_my_progress);
		pbProgress = findViewById(R.id.pb_detail_progress);
		cardMyProgress    = findViewById(R.id.card_my_progress);
		cardTeamLeaderboard = findViewById(R.id.card_team_leaderboard);
		llTeamLeaderboard   = findViewById(R.id.ll_team_leaderboard);
		btnJoinLeave = findViewById(R.id.btn_detail_join_leave);

		String challengeId = getIntent().getStringExtra(EXTRA_CHALLENGE_ID);
		if (challengeId == null) {
			finish();
			return;
		}

		loadChallenge(challengeId);
	}

	private void loadChallenge(String challengeId) {
		db.collection("challenges").document(challengeId)
				.get()
				.addOnSuccessListener(doc -> {
					if (!doc.exists()) {
						finish();
						return;
					}
					Challenge c = doc.toObject(Challenge.class);
					if (c == null) {
						finish();
						return;
					}
					c.setChallengeId(doc.getId());
					loadedChallenge = c;
					populateUI(c);
				})
				.addOnFailureListener(e -> {
					Toast.makeText(this, "Failed to load challenge.", Toast.LENGTH_SHORT).show();
					finish();
				});
	}

	private void populateUI(Challenge c) {
		// Title + description
		tvTitle.setText(c.getTitle() != null ? c.getTitle() : "Challenge");
		tvDescription.setText((c.getDescription() != null && !c.getDescription().isEmpty())
				? c.getDescription() : "No description provided.");

		// Category icon + label
		String goalType = c.getGoalType();
		if (goalType != null) {
			switch (goalType) {
			case Challenge.GOAL_TYPE_TRANSPORT:
				tvIcon.setText("🚴");
				tvCategory.setText("Transport");
				break;
			case Challenge.GOAL_TYPE_ENERGY:
				tvIcon.setText("⚡");
				tvCategory.setText("Energy");
				break;
			case Challenge.GOAL_TYPE_WASTE:
				tvIcon.setText("♻");
				tvCategory.setText("Waste");
				break;
			default:
				tvIcon.setText("🌍");
				tvCategory.setText("Overall");
				break;
			}
		}

		// Target
		String unit = c.getTargetUnit() != null ? c.getTargetUnit() : "";
		tvTarget.setText(String.format(Locale.getDefault(), "%.0f %s", c.getTargetValue(), unit));

		// Participant count
		int participantCount = c.getParticipantIds() != null ? c.getParticipantIds().size() : 0;
		tvParticipants.setText(String.valueOf(participantCount));

		// Overall progress
		double totalScore = 0;
		Map<String, Double> scores = c.getParticipantScores();
		if (scores != null) {
			for (double s : scores.values()) totalScore += s;
		}
		double target = c.getTargetValue();
		int pct = target > 0 ? (int) Math.min(100, (totalScore / target) * 100) : 0;
		pbProgress.setProgress(pct);
		tvProgressText.setText(String.format(
				Locale.getDefault(),
				"%.1f / %.0f %s",
				totalScore,
				target,
				unit
		));
		tvPct.setText(pct + "% complete");

		// Current user's personal contribution
		User user = UserSession.getInstance().getCurrentUser();
		if (user != null && scores != null && scores.containsKey(user.getUserId())) {
			double myScore = scores.get(user.getUserId());
			cardMyProgress.setVisibility(View.VISIBLE);
			tvMyProgress.setText(String.format(Locale.getDefault(), "%.1f %s", myScore, unit));
		}

		// Join / Leave button
		boolean isJoined = user != null
				&& c.getParticipantIds() != null
				&& c.getParticipantIds().contains(user != null ? user.getUserId() : "");

		updateJoinLeaveButton(isJoined, c, user);

		// Team leaderboard
		loadTeamLeaderboard(c);
	}

	private void loadTeamLeaderboard(Challenge c) {
		Map<String, Double> participantScores = c.getParticipantScores();
		if (participantScores == null || participantScores.isEmpty()) return;
		double target = c.getTargetValue();
		String unit = c.getTargetUnit() != null ? c.getTargetUnit() : "";

		db.collection("clubs").get()
				.addOnSuccessListener(snap -> {
					List<String[]> clubEntries = new ArrayList<>();
					for (var doc : snap.getDocuments()) {
						Club club = doc.toObject(Club.class);
						if (club == null || club.getMemberIds() == null) continue;
						double clubScore = 0;
						for (String mid : club.getMemberIds()) {
							Double s = participantScores.get(mid);
							if (s != null) clubScore += s;
						}
						if (clubScore > 0) {
							clubEntries.add(new String[]{
									club.getName() != null ? club.getName() : doc.getId(),
									String.valueOf(clubScore),
									doc.getId()
							});
						}
					}

					if (clubEntries.isEmpty()) return;

					clubEntries.sort((a, b) ->
							Double.compare(Double.parseDouble(b[1]), Double.parseDouble(a[1])));

					runOnUiThread(() -> {
						if (cardTeamLeaderboard != null) cardTeamLeaderboard.setVisibility(View.VISIBLE);
						if (llTeamLeaderboard == null) return;
						llTeamLeaderboard.removeAllViews();

						double maxScore = Math.max(1, Double.parseDouble(clubEntries.get(0)[1]));
						String[] medals = { "🥇", "🥈", "🥉" };

						for (int i = 0; i < clubEntries.size(); i++) {
							String name  = clubEntries.get(i)[0];
							double score = Double.parseDouble(clubEntries.get(i)[1]);
							int pct = (int) ((score / maxScore) * 100);
							int barColor = i == 0 ? Color.parseColor("#FFB300")
									: Color.parseColor("#009688");
							String medal = i < 3 ? medals[i] : (i + 1) + ".";

							LinearLayout row = new LinearLayout(this);
							row.setOrientation(LinearLayout.VERTICAL);
							LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
									LinearLayout.LayoutParams.MATCH_PARENT,
									LinearLayout.LayoutParams.WRAP_CONTENT);
							rlp.setMargins(0, 0, 0, dpToPx(10));
							row.setLayoutParams(rlp);

							LinearLayout labelRow = new LinearLayout(this);
							labelRow.setOrientation(LinearLayout.HORIZONTAL);

							TextView tvMedal = new TextView(this);
							tvMedal.setText(medal);
							tvMedal.setTextSize(16);
							tvMedal.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(36), dpToPx(28)));

							TextView tvName = new TextView(this);
							tvName.setText(name);
							tvName.setTextSize(13);
							tvName.setTextColor(Color.parseColor("#212121"));
							tvName.setLayoutParams(new LinearLayout.LayoutParams(0,
									LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

							TextView tvScore = new TextView(this);
							tvScore.setText(String.format(Locale.getDefault(),
									unit.isEmpty() ? "%.0f pts" : "%.1f " + unit, score));
							tvScore.setTextSize(12);
							tvScore.setTextColor(barColor);
							tvScore.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

							labelRow.addView(tvMedal);
							labelRow.addView(tvName);
							labelRow.addView(tvScore);

							ProgressBar pb = new ProgressBar(this, null,
									android.R.attr.progressBarStyleHorizontal);
							pb.setMax(100);
							pb.setProgress(pct);
							pb.setProgressTintList(
									ColorStateList.valueOf(barColor));
							pb.setProgressBackgroundTintList(
									ColorStateList.valueOf(Color.parseColor("#E0E0E0")));
							LinearLayout.LayoutParams pblp = new LinearLayout.LayoutParams(
									LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(8));
							pblp.setMargins(0, dpToPx(4), 0, 0);
							pb.setLayoutParams(pblp);

							row.addView(labelRow);
							row.addView(pb);
							llTeamLeaderboard.addView(row);
						}
					});
				});
	}

	private int dpToPx(int dp) {
		return (int) (dp * getResources().getDisplayMetrics().density);
	}

	private void updateJoinLeaveButton(boolean isJoined, Challenge c, User user) {
		if (isJoined) {
			btnJoinLeave.setText("Leave Challenge");
			btnJoinLeave.setBackgroundTintList(
					ColorStateList.valueOf(Color.parseColor("#F44336")));
			btnJoinLeave.setOnClickListener(v -> leaveChallenge(c));
		} else {
			btnJoinLeave.setText("Join Challenge");
			btnJoinLeave.setBackgroundTintList(
					ColorStateList.valueOf(Color.parseColor("#00BCD4")));
			btnJoinLeave.setOnClickListener(v -> joinChallenge(c, user));
		}
	}

	private void joinChallenge(Challenge c, User user) {
		if (user == null) return;
		user.joinChallenge(
				c.getChallengeId(), success -> runOnUiThread(() -> {
					if (success) {
						new ProgressTracker().checkChallengeJoinBadge(user);
						Toast.makeText(
								this,
								"Joined \"" + c.getTitle() + "\"!",
								Toast.LENGTH_SHORT
						).show();
						loadChallenge(c.getChallengeId()); // Reload to reflect updated state
					} else {
						Toast.makeText(
								this,
								"Failed to join. Try again.",
								Toast.LENGTH_SHORT
						).show();
					}
				})
		);
	}

	private void leaveChallenge(Challenge c) {
		User user = UserSession.getInstance().getCurrentUser();
		if (user == null) return;
		db.collection("challenges").document(c.getChallengeId())
				.update("participantIds", FieldValue.arrayRemove(user.getUserId()))
				.addOnSuccessListener(unused -> runOnUiThread(() -> {
					Toast.makeText(
							this,
							"Left \"" + c.getTitle() + "\"",
							Toast.LENGTH_SHORT
					).show();
					loadChallenge(c.getChallengeId()); // Reload to reflect updated state
				}))
				.addOnFailureListener(e -> runOnUiThread(() ->
						Toast.makeText(
								this,
								"Failed to leave. Try again.",
								Toast.LENGTH_SHORT
						).show()));
	}
}
