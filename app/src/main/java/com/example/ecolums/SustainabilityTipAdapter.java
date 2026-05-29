package com.example.ecolums;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * RecyclerView adapter for the student-facing Sustainability Tips list.
 */
public class SustainabilityTipAdapter extends RecyclerView.Adapter<SustainabilityTipAdapter.TipViewHolder> {

	public interface OnTipClickListener {
		void onTipClick(SustainabilityTip tip);
	}

	private List<SustainabilityTip> tips;
	private final OnTipClickListener listener;

	public SustainabilityTipAdapter(List<SustainabilityTip> tips, OnTipClickListener listener) {
		this.tips = tips;
		this.listener = listener;
	}

	public void setTips(List<SustainabilityTip> tips) {
		this.tips = tips;
		notifyDataSetChanged();
	}

	@NonNull
	@Override
	public TipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.item_tip_card, parent, false);
		return new TipViewHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull TipViewHolder holder, int position) {
		holder.bind(tips.get(position), listener);
	}

	@Override
	public int getItemCount() {
		return tips == null ? 0 : tips.size();
	}

	static class TipViewHolder extends RecyclerView.ViewHolder {
		private final View bannerBg;
		private final TextView tvEmoji;
		private final TextView tvNewBadge;
		private final TextView tvCategory;
		private final TextView tvTitle;
		private final TextView tvSummary;
		private final TextView tvLikes;

		TipViewHolder(@NonNull View itemView) {
			super(itemView);
			bannerBg = itemView.findViewById(R.id.view_banner_bg);
			tvEmoji = itemView.findViewById(R.id.tv_category_emoji);
			tvNewBadge = itemView.findViewById(R.id.tv_new_badge);
			tvCategory = itemView.findViewById(R.id.tv_category_chip);
			tvTitle = itemView.findViewById(R.id.tv_tip_title);
			tvSummary = itemView.findViewById(R.id.tv_tip_summary);
			tvLikes = itemView.findViewById(R.id.tv_likes);
		}

		void bind(SustainabilityTip tip, OnTipClickListener listener) {
			tvTitle.setText(tip.getTitle());
			tvSummary.setText(tip.getSummary());
			tvCategory.setText(tip.getCategory());
			tvLikes.setText("♥ " + tip.getLikeCount());
			tvNewBadge.setVisibility(tip.isNew() ? View.VISIBLE : View.GONE);

			// Category-based colour and emoji
			int bgRes;
			String emoji;
			switch (tip.getCategory() != null ? tip.getCategory() : "") {
			case SustainabilityTip.CATEGORY_TRANSPORT:
				bgRes = R.drawable.bg_tip_transport;
				emoji = "🚌";
				break;
			case SustainabilityTip.CATEGORY_ENERGY:
				bgRes = R.drawable.bg_tip_energy;
				emoji = "⚡";
				break;
			case SustainabilityTip.CATEGORY_WASTE:
				bgRes = R.drawable.bg_tip_waste;
				emoji = "♻";
				break;
			default:
				bgRes = R.drawable.bg_tip_general;
				emoji = "🌿";
				break;
			}
			bannerBg.setBackgroundResource(bgRes);
			tvEmoji.setText(emoji);

			itemView.setOnClickListener(v -> listener.onTipClick(tip));
		}
	}
}
