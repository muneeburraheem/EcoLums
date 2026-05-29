package com.example.ecolums;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for the Logs tab list.
 */
public class ActivityLogAdapter extends RecyclerView.Adapter<ActivityLogAdapter.VH> {

	private final List<ActivityLog> logs;

	public ActivityLogAdapter(List<ActivityLog> logs) {
		this.logs = logs;
	}

	@NonNull
	@Override
	public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.item_activity_log, parent, false);
		return new VH(v);
	}

	@Override
	public void onBindViewHolder(@NonNull VH h, int pos) {
		ActivityLog log = logs.get(pos);

		switch (log.getCategory()) {
		case ActivityLog.CATEGORY_TRANSPORT:
			h.tvIcon.setText("🚌");
			h.tvTitle.setText(modeLabel(log.getTransportMode()));
			h.tvDetail.setText(String.format(Locale.getDefault(), "%.1f km", log.getDistanceKm()));
			break;
		case ActivityLog.CATEGORY_ENERGY:
			h.tvIcon.setText("⚡");
			h.tvTitle.setText("Energy Saved");
			h.tvDetail.setText(String.format(Locale.getDefault(), "%.1f kWh", log.getEnergyKwh()));
			break;
		default:
			h.tvIcon.setText("♻");
			h.tvTitle.setText(wasteLabel(log.getWasteType()));
			h.tvDetail.setText(String.format(Locale.getDefault(), "%.1f kg", log.getWasteKg()));
		}

		h.tvCo2.setText(String.format(Locale.getDefault(), "+%.2f", log.getCo2EquivalentKg()));

		if (log.getDate() != null) {
			Date d = log.getDate().toDate();
			h.tvDate.setText(new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(d));
		}
	}

	@Override
	public int getItemCount() {
		return logs.size();
	}

	private String modeLabel(String mode) {
		if (mode == null) return "Transport";
		switch (mode) {
		case "BUS":
			return "Used Public Transport";
		case "BIKE":
			return "Bike Ride";
		case "WALK":
			return "Walked";
		case "TRAIN":
			return "Train Journey";
		case "CAR":
			return "Car Trip";
		default:
			return "Transport";
		}
	}

	private String wasteLabel(String type) {
		if (type == null) return "Waste";
		switch (type) {
		case "RECYCLE":
			return "Recycled Waste";
		case "COMPOST":
			return "Composted Waste";
		default:
			return "Landfill Waste";
		}
	}

	static class VH extends RecyclerView.ViewHolder {
		TextView tvIcon, tvTitle, tvDetail, tvCo2, tvDate;

		VH(View v) {
			super(v);
			tvIcon = v.findViewById(R.id.tv_category_icon);
			tvTitle = v.findViewById(R.id.tv_log_title);
			tvDetail = v.findViewById(R.id.tv_log_detail);
			tvCo2 = v.findViewById(R.id.tv_co2_saved);
			tvDate = v.findViewById(R.id.tv_log_date);
		}
	}
}
