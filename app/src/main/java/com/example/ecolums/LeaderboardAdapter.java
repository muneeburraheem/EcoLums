package com.example.ecolums;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for the leaderboard ranking list.
 */
public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.VH> {

	private final List<Leaderboard.RankEntry> entries;

	public LeaderboardAdapter(List<Leaderboard.RankEntry> entries) {
		this.entries = entries;
	}

	@NonNull
	@Override
	public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.item_leaderboard_entry, parent, false);
		return new VH(v);
	}

	@Override
	public void onBindViewHolder(@NonNull VH h, int pos) {
		Leaderboard.RankEntry e = entries.get(pos);

		// Medal emoji for top 3
		String rankDisplay;
		switch (e.rank) {
			case 1:  rankDisplay = "🥇"; break;
			case 2:  rankDisplay = "🥈"; break;
			case 3:  rankDisplay = "🥉"; break;
			default: rankDisplay = String.valueOf(e.rank); break;
		}
		h.tvRank.setText(rankDisplay);
		h.tvName.setText(e.entityName != null ? e.entityName : "Unknown");
		h.tvScore.setText(String.format(Locale.getDefault(), "%.0f pts", e.score));

		// Highlight current user's row
		User currentUser = UserSession.getInstance().getCurrentUser();
		boolean isCurrentUser = currentUser != null
				&& e.entityId != null
				&& e.entityId.equals(currentUser.getUserId());

		if (isCurrentUser) {
			h.itemView.setBackgroundColor(android.graphics.Color.parseColor("#E0F7FA"));
		} else {
			h.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
		}

		// Scale bar: top scorer = 100%, others scaled relative to #1
		if (!entries.isEmpty() && entries.get(0).score > 0) {
			int pct = (int) ((e.score / entries.get(0).score) * 100);
			h.pbBar.setProgress(pct);
		} else {
			h.pbBar.setProgress(0);
		}
	}

	@Override
	public int getItemCount() {
		return entries.size();
	}

	static class VH extends RecyclerView.ViewHolder {
		TextView tvRank, tvName, tvScore;
		ProgressBar pbBar;

		VH(View v) {
			super(v);
			tvRank = v.findViewById(R.id.tv_rank_number);
			tvName = v.findViewById(R.id.tv_rank_name);
			tvScore = v.findViewById(R.id.tv_rank_score);
			pbBar = v.findViewById(R.id.pb_rank_bar);
		}
	}
}
