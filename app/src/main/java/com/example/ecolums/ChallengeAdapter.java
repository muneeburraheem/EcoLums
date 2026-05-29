package com.example.ecolums;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RecyclerView adapter for the Challenges tab gallery.
 */
public class ChallengeAdapter extends RecyclerView.Adapter<ChallengeAdapter.VH> {

	public interface OnJoinClickListener {
		void onJoin(Challenge challenge);
	}

	public interface OnLeaveClickListener {
		void onLeave(Challenge challenge);
	}

	public interface OnChallengeClickListener {
		void onClick(Challenge challenge);
	}

	private final List<Challenge> challenges;
	private final String currentUserId;
	private final OnJoinClickListener joinListener;
	private final OnLeaveClickListener leaveListener;
	private final OnChallengeClickListener clickListener;

	public ChallengeAdapter(
			List<Challenge> challenges,
			String currentUserId,
			OnJoinClickListener joinListener,
			OnLeaveClickListener leaveListener,
			OnChallengeClickListener clickListener
	) {
		this.challenges = challenges;
		this.currentUserId = currentUserId;
		this.joinListener = joinListener;
		this.leaveListener = leaveListener;
		this.clickListener = clickListener;
	}

	@NonNull
	@Override
	public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.item_challenge, parent, false);
		return new VH(v);
	}

	@Override
	public void onBindViewHolder(@NonNull VH h, int pos) {
		Challenge c = challenges.get(pos);

		// Card click → detail screen
		h.itemView.setOnClickListener(v -> {
			if (clickListener != null) clickListener.onClick(c);
		});

		// Title
		h.tvTitle.setText(c.getTitle() != null ? c.getTitle() : "Challenge");

		// Description
		if (c.getDescription() != null && !c.getDescription().isEmpty()) {
			h.tvDescription.setText(c.getDescription());
			h.tvDescription.setVisibility(View.VISIBLE);
		} else {
			h.tvDescription.setVisibility(View.GONE);
		}

		// Icon based on goal type
		String goalType = c.getGoalType();
		if (goalType != null) {
			switch (goalType) {
			case Challenge.GOAL_TYPE_TRANSPORT:
				h.tvIcon.setText("🚴");
				break;
			case Challenge.GOAL_TYPE_ENERGY:
				h.tvIcon.setText("⚡");
				break;
			case Challenge.GOAL_TYPE_WASTE:
				h.tvIcon.setText("♻");
				break;
			default:
				h.tvIcon.setText("🌍");
				break;
			}
		}

		// Real progress: sum of all participant scores vs target
		double totalScore = 0;
		Map<String, Double> scores = c.getParticipantScores();
		if (scores != null) {
			for (double s : scores.values()) totalScore += s;
		}
		double target = c.getTargetValue();
		int progressPct = (target > 0) ? (int) Math.min(100, (totalScore / target) * 100) : 0;
		h.pbProgress.setProgress(progressPct);

		String unit = c.getTargetUnit() != null ? c.getTargetUnit() : "";
		h.tvProgress.setText(String.format(
				Locale.getDefault(),
				"%.1f / %.0f %s",
				totalScore,
				target,
				unit
		));

		// Join / Leave toggle
		boolean isJoined = currentUserId != null
				&& c.getParticipantIds() != null
				&& c.getParticipantIds().contains(currentUserId);

		if (isJoined) {
			h.btnJoin.setText("Leave");
			h.btnJoin.setBackgroundTintList(
					ColorStateList.valueOf(Color.parseColor("#F44336")));
			h.btnJoin.setOnClickListener(v -> {
				if (leaveListener != null) leaveListener.onLeave(c);
			});
		} else {
			h.btnJoin.setText("Join");
			h.btnJoin.setBackgroundTintList(
					ColorStateList.valueOf(Color.parseColor("#00BCD4")));
			h.btnJoin.setOnClickListener(v -> {
				if (joinListener != null) joinListener.onJoin(c);
			});
		}
	}

	@Override
	public int getItemCount() {
		return challenges.size();
	}

	static class VH extends RecyclerView.ViewHolder {
		TextView tvIcon, tvTitle, tvDescription, tvProgress;
		ProgressBar pbProgress;
		MaterialButton btnJoin;

		VH(View v) {
			super(v);
			tvIcon = v.findViewById(R.id.tv_challenge_icon);
			tvTitle = v.findViewById(R.id.tv_challenge_title);
			tvDescription = v.findViewById(R.id.tv_challenge_description);
			tvProgress = v.findViewById(R.id.tv_challenge_progress);
			pbProgress = v.findViewById(R.id.pb_challenge_progress);
			btnJoin = v.findViewById(R.id.btn_join_challenge);
		}
	}
}
