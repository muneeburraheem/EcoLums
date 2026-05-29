package com.example.ecolums;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * RecyclerView adapter for the admin Tips tab; includes a delete action per row.
 */
public class AdminTipAdapter extends RecyclerView.Adapter<AdminTipAdapter.AdminTipViewHolder> {

	public interface OnAdminTipListener {
		void onEdit(SustainabilityTip tip);

		void onDelete(SustainabilityTip tip);
	}

	private List<SustainabilityTip> tips;
	private final OnAdminTipListener listener;

	public AdminTipAdapter(List<SustainabilityTip> tips, OnAdminTipListener listener) {
		this.tips = tips;
		this.listener = listener;
	}

	public void setTips(List<SustainabilityTip> tips) {
		this.tips = tips;
		notifyDataSetChanged();
	}

	@NonNull
	@Override
	public AdminTipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.item_admin_tip, parent, false);
		return new AdminTipViewHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull AdminTipViewHolder holder, int position) {
		holder.bind(tips.get(position), listener);
	}

	@Override
	public int getItemCount() {
		return tips == null ? 0 : tips.size();
	}

	static class AdminTipViewHolder extends RecyclerView.ViewHolder {
		private final TextView tvEmoji;
		private final TextView tvTitle;
		private final TextView tvCategory;
		private final MaterialButton btnEdit;
		private final MaterialButton btnDelete;

		AdminTipViewHolder(@NonNull View itemView) {
			super(itemView);
			tvEmoji = itemView.findViewById(R.id.tv_admin_tip_emoji);
			tvTitle = itemView.findViewById(R.id.tv_admin_tip_title);
			tvCategory = itemView.findViewById(R.id.tv_admin_tip_category);
			btnEdit = itemView.findViewById(R.id.btn_edit_tip);
			btnDelete = itemView.findViewById(R.id.btn_delete_tip);
		}

		void bind(SustainabilityTip tip, OnAdminTipListener listener) {
			tvTitle.setText(tip.getTitle());
			tvCategory.setText(tip.getCategory());

			String emoji;
			switch (tip.getCategory() != null ? tip.getCategory() : "") {
			case SustainabilityTip.CATEGORY_TRANSPORT:
				emoji = "🚌";
				break;
			case SustainabilityTip.CATEGORY_ENERGY:
				emoji = "⚡";
				break;
			case SustainabilityTip.CATEGORY_WASTE:
				emoji = "♻";
				break;
			default:
				emoji = "🌿";
				break;
			}
			tvEmoji.setText(emoji);

			btnEdit.setOnClickListener(v -> listener.onEdit(tip));
			btnDelete.setOnClickListener(v -> listener.onDelete(tip));
		}
	}
}
