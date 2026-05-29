package com.example.ecolums;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RecyclerView adapter for the admin Audit Log tab. Renders raw Firestore map data.
 */
public class AuditLogAdapter extends RecyclerView.Adapter<AuditLogAdapter.VH> {

	private final List<Map<String, Object>> entries;

	public AuditLogAdapter(List<Map<String, Object>> entries) {
		this.entries = entries;
	}

	@NonNull
	@Override
	public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.item_audit_log, parent, false);
		return new VH(v);
	}

	@Override
	public void onBindViewHolder(@NonNull VH h, int pos) {
		Map<String, Object> e = entries.get(pos);

		String actionType = getString(e, "actionType");
		String description = getString(e, "description");
		String adminEmail = getString(e, "adminEmail");

		h.tvAction.setText(actionType);
		h.tvDescription.setText(description);
		h.tvAdmin.setText("By: " + (adminEmail != null ? adminEmail : "unknown admin"));

		// Color the left stripe based on action type
		int stripeColor;
		if ("FLAGGED_ACTIVITY".equals(actionType)) {
			stripeColor = Color.parseColor("#FF5722"); // deep orange — anomaly alert
		} else if (actionType != null && actionType.contains("DELETE")) {
			stripeColor = Color.parseColor("#F44336"); // red
		} else if (actionType != null && actionType.contains("PRIVACY")) {
			stripeColor = Color.parseColor("#FF9800"); // orange
		} else if (actionType != null && actionType.contains("EXPORT")) {
			stripeColor = Color.parseColor("#2196F3"); // blue
		} else {
			stripeColor = Color.parseColor("#00BCD4"); // teal default
		}
		h.stripe.setBackgroundColor(stripeColor);

		// Format timestamp
		Object ts = e.get("timestamp");
		if (ts instanceof Timestamp) {
			Date d = ((Timestamp) ts).toDate();
			h.tvTimestamp.setText(
					new SimpleDateFormat("MMM d\nhh:mm a", Locale.getDefault()).format(d));
		} else {
			h.tvTimestamp.setText("—");
		}
	}

	@Override
	public int getItemCount() {
		return entries.size();
	}

	private String getString(Map<String, Object> map, String key) {
		Object val = map.get(key);
		return val != null ? val.toString() : "";
	}

	static class VH extends RecyclerView.ViewHolder {
		View stripe;
		TextView tvAction, tvDescription, tvAdmin, tvTimestamp;

		VH(View v) {
			super(v);
			stripe = v.findViewById(R.id.view_action_stripe);
			tvAction = v.findViewById(R.id.tv_audit_action);
			tvDescription = v.findViewById(R.id.tv_audit_description);
			tvAdmin = v.findViewById(R.id.tv_audit_admin);
			tvTimestamp = v.findViewById(R.id.tv_audit_timestamp);
		}
	}
}
