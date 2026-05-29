package com.example.ecolums;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Admin tab: view and edit CO₂ calculation metrics (AC1–AC5 of the
 * "Update Impact Calculation Metrics" user story).
 * <p>
 * AC1  – List and edit specific formulas for each metric.
 * AC2  – Covers Transport, Energy, and Waste categories.
 * AC3  – Writes an audit log entry on every metric change.
 * AC4  – Triggers campus-wide milestone goal recalculation after a metric update.
 * AC5  – Info icon reveals the reference standard for each metric.
 */
public class AdminMetricsFragment extends Fragment {

	private RecyclerView rvMetrics;
	private TextView tvCount;
	private final List<CalculationMetric> metrics = new ArrayList<>();
	private MetricAdapter adapter;
	private FirebaseFirestore db;

	@Nullable
	@Override
	public View onCreateView(
			@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState
	) {
		View view = inflater.inflate(R.layout.fragment_admin_metrics, container, false);
		db = FirebaseFirestore.getInstance();

		rvMetrics = view.findViewById(R.id.rv_metrics);
		tvCount = view.findViewById(R.id.tv_metrics_count);

		adapter = new MetricAdapter();
		rvMetrics.setLayoutManager(new LinearLayoutManager(getContext()));
		rvMetrics.setAdapter(adapter);

		view.findViewById(R.id.btn_refresh_metrics).setOnClickListener(v -> loadMetrics());

		loadMetrics();
		return view;
	}

	// ── Data loading ─────────────────────────────────────────────────────────

	private void loadMetrics() {
		tvCount.setText("Loading…");
		db.collection("calculationMetrics").get()
				.addOnSuccessListener(snap -> {
					metrics.clear();
					for (var doc : snap.getDocuments()) {
						CalculationMetric m = doc.toObject(CalculationMetric.class);
						if (m != null) {
							m.setMetricId(doc.getId());
							metrics.add(m);
						}
					}
					adapter.notifyDataSetChanged();
					tvCount.setText(metrics.size() + " metric(s) — tap Edit to change a value");
				})
				.addOnFailureListener(e -> tvCount.setText("Failed to load metrics"));
	}

	// ── Edit dialog (AC1) ────────────────────────────────────────────────────

	private void showEditDialog(CalculationMetric metric) {
		if (getContext() == null) return;

		// Build a simple dialog with an EditText pre-filled with the current value
		LinearLayout layout = new LinearLayout(getContext());
		layout.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (16 * getResources().getDisplayMetrics().density);
		layout.setPadding(pad, pad, pad, 0);

		TextView tvLabel = new TextView(getContext());
		tvLabel.setText(metric.getDisplayName() + "\nUnit: " + metric.getUnit());
		tvLabel.setTextSize(13);
		layout.addView(tvLabel);

		EditText etValue = new EditText(getContext());
		etValue.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
				| android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
				| android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
		etValue.setText(String.format(Locale.getDefault(), "%.4f", metric.getValue()));
		layout.addView(etValue);

		new AlertDialog.Builder(getContext())
				.setTitle("Edit: " + metric.getDisplayName())
				.setView(layout)
				.setPositiveButton(
						"Save", (dialog, which) -> {
							String raw = etValue.getText().toString().trim();
							if (raw.isEmpty()) return;
							double newValue;
							try {
								newValue = Double.parseDouble(raw);
							} catch (NumberFormatException e) {
								Toast.makeText(
										getContext(),
										"Invalid number",
										Toast.LENGTH_SHORT
								).show();
								return;
							}
							saveMetricUpdate(metric, newValue);
						}
				)
				.setNegativeButton("Cancel", null)
				.show();
	}

	// ── Persist update + audit log (AC1 + AC3) ───────────────────────────────

	private void saveMetricUpdate(CalculationMetric metric, double newValue) {
		User admin = UserSession.getInstance().getCurrentUser();
		String adminId = admin != null ? admin.getUserId() : "unknown";
		double oldValue = metric.getValue();

		// 1. Update the metric document
		Map<String, Object> updates = new HashMap<>();
		updates.put("value", newValue);
		updates.put("lastUpdated", Timestamp.now());
		updates.put("lastUpdatedByAdminId", adminId);

		db.collection("calculationMetrics").document(metric.getMetricId())
				.update(updates)
				.addOnSuccessListener(unused -> {
					// Update in-memory copy
					metric.setValue(newValue);
					metric.setLastUpdated(Timestamp.now());
					metric.setLastUpdatedByAdminId(adminId);
					adapter.notifyDataSetChanged();

					// 2. Write audit log entry (AC3)
					writeAuditLog(adminId, metric, oldValue, newValue);

					// 3. Trigger campus-wide recalculation (AC4)
					triggerCampusGoalRecalculation();

					if (getContext() != null) {
						Toast.makeText(
								getContext(),
								metric.getDisplayName() + " updated to " + newValue,
								Toast.LENGTH_SHORT
						).show();
					}
				})
				.addOnFailureListener(e -> {
					if (getContext() != null) {
						Toast.makeText(
								getContext(), "Save failed: " + e.getMessage(),
								Toast.LENGTH_SHORT
						).show();
					}
				});
	}

	// AC3 – Audit log
	private void writeAuditLog(
			String adminId, CalculationMetric metric,
			double oldValue, double newValue
	) {
		Map<String, Object> entry = new HashMap<>();
		entry.put("actionType", AuditLogEntry.ACTION_METRIC_UPDATED);
		entry.put("adminId", adminId);
		entry.put("targetEntityId", metric.getMetricKey());
		entry.put(
				"description", "Updated " + metric.getDisplayName()
						+ " from " + oldValue + " to " + newValue + " " + metric.getUnit()
		);
		entry.put("previousValue", String.valueOf(oldValue));
		entry.put("newValue", String.valueOf(newValue));
		entry.put("timestamp", Timestamp.now());
		db.collection("auditLog").add(entry);
	}

	// AC4 – Recalculate campus-wide CO₂ totals using current metric factors
	private void triggerCampusGoalRecalculation() {
		// Load all current metric factors
		db.collection("calculationMetrics").get()
				.addOnSuccessListener(metricsSnap -> {
					Map<String, Double> factors = new HashMap<>();
					for (var doc : metricsSnap.getDocuments()) {
						String key = doc.getString("metricKey");
						Double val = doc.getDouble("value");
						if (key != null && val != null) factors.put(key, val);
					}

					// Fetch all users, then their logs, recalculate CO₂ totals
					db.collection("users").get()
							.addOnSuccessListener(usersSnap -> {
								if (usersSnap.isEmpty()) return;

								List<String> userIds = new ArrayList<>();
								for (var doc : usersSnap.getDocuments()) {
									userIds.add(doc.getId());
								}

								// Use an AtomicDouble-equivalent via array to sum CO₂
								double[] totalCo2 = { 0.0 };
								AtomicInteger remaining = new AtomicInteger(userIds.size());

								for (String uid : userIds) {
									db.collection("users").document(uid)
											.collection("activityLogs").get()
											.addOnSuccessListener(logsSnap -> {
												for (var logDoc : logsSnap.getDocuments()) {
													// Recalculate CO₂ for this log using current factors
													String category = logDoc.getString("category");
													double co2 = recalcCo2(
															logDoc,
															category,
															factors
													);
													totalCo2[0] += co2;
												}
												if (remaining.decrementAndGet() == 0) {
													// Update campus goal with recalculated total
													db.collection("campusGoals")
															.document("goal_main")
															.update("currentProgress", totalCo2[0]);
												}
											})
											.addOnFailureListener(e -> {
												if (remaining.decrementAndGet() == 0) {
													db.collection("campusGoals")
															.document("goal_main")
															.update("currentProgress", totalCo2[0]);
												}
											});
								}
							});
				});
	}

	/**
	 * Re-computes CO₂ for a raw Firestore log document given updated metric factors.
	 */
	private double recalcCo2(
			com.google.firebase.firestore.DocumentSnapshot doc,
			String category, Map<String, Double> factors
	) {
		if (category == null) return 0;
		switch (category) {
		case ActivityLog.CATEGORY_TRANSPORT: {
			String mode = doc.getString("transportMode");
			Double dist = doc.getDouble("distanceKm");
			if (mode == null || dist == null) return 0;
			String key = mode + "_PER_KM"; // e.g. BIKE_PER_KM
			Double factor = factors.get(key);
			return factor != null ? dist * factor : 0;
		}
		case ActivityLog.CATEGORY_ENERGY: {
			Double kwh = doc.getDouble("energyKwh");
			Double factor = factors.get(CalculationMetric.KEY_ENERGY_PER_KWH);
			return (kwh != null && factor != null) ? kwh * factor : 0;
		}
		case ActivityLog.CATEGORY_WASTE: {
			String type = doc.getString("wasteType");
			Double kg = doc.getDouble("wasteKg");
			if (type == null || kg == null) return 0;
			String key;
			switch (type) {
			case ActivityLog.WASTE_RECYCLE:
				key = CalculationMetric.KEY_WASTE_RECYCLE_KG;
				break;
			case ActivityLog.WASTE_COMPOST:
				key = CalculationMetric.KEY_WASTE_COMPOST_KG;
				break;
			default:
				key = CalculationMetric.KEY_WASTE_LANDFILL_KG;
				break;
			}
			Double factor = factors.get(key);
			return (factor != null) ? kg * factor : 0;
		}
		default:
			return 0;
		}
	}

	// ── Info dialog (AC5) ─────────────────────────────────────────────────────

	private void showInfoDialog(CalculationMetric metric) {
		if (getContext() == null) return;
		String msg = metric.getDisplayName() + "\n\n"
				+ "Current value: " + metric.getValue() + " " + metric.getUnit() + "\n"
				+ "Reference standard: " + (metric.getReferenceStandard() != null
				? metric.getReferenceStandard() : "N/A");
		new AlertDialog.Builder(getContext())
				.setTitle("ℹ Carbon Factor Info")
				.setMessage(msg)
				.setPositiveButton("OK", null)
				.show();
	}

	// ── Category label helper ─────────────────────────────────────────────────

	private String getCategoryLabel(CalculationMetric m) {
		String key = m.getMetricKey() != null ? m.getMetricKey() : "";
		if (key.contains("PER_KM")) return "🚴 Transport";
		if (key.contains("ENERGY")) return "⚡ Energy";
		if (key.contains("KG")) return "♻ Waste";
		return "Other";
	}

	// ── Inner adapter ─────────────────────────────────────────────────────────

	private class MetricAdapter extends RecyclerView.Adapter<MetricAdapter.VH> {

		@NonNull
		@Override
		public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View v = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.item_admin_metric, parent, false);
			return new VH(v);
		}

		@Override
		public void onBindViewHolder(@NonNull VH h, int pos) {
			CalculationMetric m = metrics.get(pos);

			h.tvName.setText(m.getDisplayName() != null ? m.getDisplayName() : m.getMetricKey());
			h.tvCategory.setText(getCategoryLabel(m));
			h.tvValue.setText(String.format(
					Locale.getDefault(),
					"%.4f %s", m.getValue(), m.getUnit() != null ? m.getUnit() : ""
			));
			h.tvRef.setText(m.getReferenceStandard() != null ? m.getReferenceStandard() : "");

			h.btnEdit.setOnClickListener(v -> showEditDialog(m));
			h.tvInfo.setOnClickListener(v -> showInfoDialog(m));
		}

		@Override
		public int getItemCount() {
			return metrics.size();
		}

		class VH extends RecyclerView.ViewHolder {
			TextView tvCategory, tvName, tvValue, tvRef, tvInfo;
			com.google.android.material.button.MaterialButton btnEdit;

			VH(View v) {
				super(v);
				tvCategory = v.findViewById(R.id.tv_metric_category);
				tvName = v.findViewById(R.id.tv_metric_name);
				tvValue = v.findViewById(R.id.tv_metric_value);
				tvRef = v.findViewById(R.id.tv_metric_reference);
				tvInfo = v.findViewById(R.id.tv_metric_info);
				btnEdit = v.findViewById(R.id.btn_edit_metric);
			}
		}
	}
}
