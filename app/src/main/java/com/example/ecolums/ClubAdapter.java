package com.example.ecolums;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for the Clubs tab gallery.
 */
public class ClubAdapter extends RecyclerView.Adapter<ClubAdapter.VH> {

	public interface OnClubClickListener {
		void onClick(Club club, String clubId);
	}

	private final List<Club> clubs;
	private final List<String> clubIds;
	private final OnClubClickListener clickListener;

	public ClubAdapter(List<Club> clubs, List<String> clubIds, OnClubClickListener clickListener) {
		this.clubs = clubs;
		this.clubIds = clubIds;
		this.clickListener = clickListener;
	}

	@NonNull
	@Override
	public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.item_club, parent, false);
		return new VH(v);
	}

	@Override
	public void onBindViewHolder(@NonNull VH h, int pos) {
		Club c = clubs.get(pos);
		String id = pos < clubIds.size() ? clubIds.get(pos) : "";

		h.itemView.setOnClickListener(v -> {
			if (clickListener != null) clickListener.onClick(c, id);
		});

		h.tvName.setText(c.getName() != null ? c.getName() : "Club");

		if (c.getDescription() != null && !c.getDescription().isEmpty()) {
			h.tvDescription.setText(c.getDescription());
			h.tvDescription.setVisibility(View.VISIBLE);
		} else {
			h.tvDescription.setVisibility(View.GONE);
		}

		int memberCount = c.getMemberIds() != null ? c.getMemberIds().size() : 0;
		h.tvMembers.setText("👥 " + memberCount + " member" + (memberCount == 1 ? "" : "s"));

		h.tvPoints.setText(String.format(Locale.getDefault(), "⭐ %.0f pts", c.getTotalPoints()));
	}

	@Override
	public int getItemCount() {
		return clubs.size();
	}

	static class VH extends RecyclerView.ViewHolder {
		TextView tvName, tvDescription, tvMembers, tvPoints;

		VH(View v) {
			super(v);
			tvName = v.findViewById(R.id.tv_club_name);
			tvDescription = v.findViewById(R.id.tv_club_description);
			tvMembers = v.findViewById(R.id.tv_club_members);
			tvPoints = v.findViewById(R.id.tv_club_points);
		}
	}
}
