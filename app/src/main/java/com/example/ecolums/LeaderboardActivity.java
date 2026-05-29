package com.example.ecolums;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen leaderboard matching the mockup:
 * - Hello banner with current user name
 * - Rank + Score dark cards
 * - Global / Weekly toggle
 * - RecyclerView of all ranked users
 */
public class LeaderboardActivity extends AppCompatActivity {

	private TextView tvHello, tvUserRank, tvUserScore, tvTabGlobal, tvTabWeekly;
	private RecyclerView rvLeaderboard;
	private LeaderboardAdapter adapter;
	private final List<Leaderboard.RankEntry> rankList = new ArrayList<>();
	private final Leaderboard leaderboard = new Leaderboard();
	private boolean showingAllTime = true;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_leaderboard);

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		toolbar.setNavigationOnClickListener(v -> finish());

		tvHello = findViewById(R.id.tv_hello_user);
		tvUserRank = findViewById(R.id.tv_user_rank);
		tvUserScore = findViewById(R.id.tv_user_score);
		tvTabGlobal = findViewById(R.id.tab_global);
		tvTabWeekly = findViewById(R.id.tab_weekly);
		rvLeaderboard = findViewById(R.id.rv_leaderboard);

		adapter = new LeaderboardAdapter(rankList);
		rvLeaderboard.setLayoutManager(new LinearLayoutManager(this));
		rvLeaderboard.setAdapter(adapter);

		User user = UserSession.getInstance().getCurrentUser();
		if (user != null) {
			tvHello.setText("Hello " + user.getName() + "!");
			tvUserScore.setText(String.format(
					Locale.getDefault(),
					"%.0f Points", user.getTotalPoints()
			));
		}

		tvTabGlobal.setOnClickListener(v -> {
			if (!showingAllTime) {
				showingAllTime = true;
				setTabSelected(tvTabGlobal, tvTabWeekly);
				loadRankings(Leaderboard.TIMEFRAME_ALL_TIME);
			}
		});

		tvTabWeekly.setOnClickListener(v -> {
			if (showingAllTime) {
				showingAllTime = false;
				setTabSelected(tvTabWeekly, tvTabGlobal);
				loadRankings(Leaderboard.TIMEFRAME_WEEKLY);
			}
		});

		loadRankings(Leaderboard.TIMEFRAME_ALL_TIME);
	}

	private void loadRankings(String timeframe) {
		leaderboard.getUserRankings(
				timeframe, rankings -> {
					rankList.clear();
					rankList.addAll(rankings);
					runOnUiThread(() -> {
						adapter.notifyDataSetChanged();
						// Update current user's rank card
						User user = UserSession.getInstance().getCurrentUser();
						if (user == null) return;
						for (Leaderboard.RankEntry e : rankings) {
							if (e.entityId != null && e.entityId.equals(user.getUserId())) {
								String suffix = getRankSuffix(e.rank);
								tvUserRank.setText("🥇 " + e.rank + suffix + " Place!");
								break;
							}
						}
					});
				}
		);
	}

	private String getRankSuffix(int rank) {
		if (rank == 1) return "st";
		if (rank == 2) return "nd";
		if (rank == 3) return "rd";
		return "th";
	}

	private void setTabSelected(TextView selected, TextView unselected) {
		selected.setTextColor(getColor(R.color.text_primary));
		selected.setTypeface(null, android.graphics.Typeface.BOLD);
		unselected.setTextColor(getColor(R.color.text_hint));
		unselected.setTypeface(null, android.graphics.Typeface.NORMAL);
	}
}
