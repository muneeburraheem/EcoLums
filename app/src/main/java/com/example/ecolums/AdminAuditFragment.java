package com.example.ecolums;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin tab fragment that displays the immutable audit log.
 *
 * <p>Reads entries from the Firestore {@code auditLog} collection and renders
 * them in reverse-chronological order using {@link AuditLogAdapter}. Entries are
 * read-only and represent privileged actions taken by admins (see
 * {@link AuditLogEntry} for the full set of action types).</p>
 * <p>
 * Outstanding issues: Pagination / infinite-scroll is not yet implemented;
 * the current query loads all audit log entries at once.
 */
public class AdminAuditFragment extends Fragment {

	private RecyclerView rvAudit;
	private TextView tvAuditCount;
	private AuditLogAdapter adapter;

	// storing raw Firestore documents as key-value maps
	private final List<Map<String, Object>> entries = new ArrayList<>();

	private FirebaseFirestore db;

	@Nullable
	@Override
	public View onCreateView(
			@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState
	) {

		View view = inflater.inflate(R.layout.fragment_admin_audit, container, false);

		db = FirebaseFirestore.getInstance();

		rvAudit = view.findViewById(R.id.rv_audit_log);
		tvAuditCount = view.findViewById(R.id.tv_audit_count);

		adapter = new AuditLogAdapter(entries);

		rvAudit.setLayoutManager(new LinearLayoutManager(getContext()));
		rvAudit.setAdapter(adapter);

		// reload button
		view.findViewById(R.id.btn_refresh_audit)
				.setOnClickListener(v -> loadAuditLog());

		loadAuditLog();
		return view;
	}

	private void loadAuditLog() {
		entries.clear();
		adapter.notifyDataSetChanged();

		// Step 1: load admin audit actions
		db.collection("auditLog")
				.orderBy("timestamp", Query.Direction.DESCENDING)
				.limit(50)
				.get()
				.addOnSuccessListener(querySnapshot -> {
					for (var doc : querySnapshot.getDocuments()) {
						Map<String, Object> data = doc.getData();
						if (data != null) {
							data.put("_docId", doc.getId());
							entries.add(data);
						}
					}
					adapter.notifyDataSetChanged();

					// Step 2: append flagged activity logs from all users
					loadFlaggedActivities();
				})
				.addOnFailureListener(e ->
						tvAuditCount.setText("Failed to load audit log"));
	}

	/**
	 * Performs a collection-group query across all {@code activityLogs} sub-collections
	 * to find entries marked as suspicious by the on-device anomaly detector.
	 * Results are appended to the existing audit list with a FLAGGED_ACTIVITY action type.
	 */
	private void loadFlaggedActivities() {
		db.collectionGroup("activityLogs")
				.whereEqualTo("isFlagged", true)
				.orderBy("date", Query.Direction.DESCENDING)
				.limit(30)
				.get()
				.addOnSuccessListener(querySnapshot -> {
					int flaggedCount = 0;
					for (QueryDocumentSnapshot doc : querySnapshot) {
						Map<String, Object> raw = doc.getData();
						if (raw == null) continue;

						// Build a synthetic audit entry compatible with AuditLogAdapter
						Map<String, Object> entry = new HashMap<>();
						entry.put("actionType",  "FLAGGED_ACTIVITY");
						entry.put("adminEmail",  "user: " + raw.getOrDefault("userId", "unknown"));

						String category = String.valueOf(raw.getOrDefault("category", "?"));
						String mode     = modeLabel(raw);
						double score    = raw.containsKey("anomalyScore")
								? ((Number) raw.get("anomalyScore")).doubleValue() : 0.0;
						entry.put("description", String.format(Locale.getDefault(),
								"%s  %s  anomaly score: %.4f", category, mode, score));

						// Reuse timestamp field so the adapter can format it
						Object date = raw.get("date");
						entry.put("timestamp", date instanceof Timestamp ? date : null);
						entry.put("_docId", doc.getId());

						entries.add(0, entry);  // prepend so flagged entries appear first
						flaggedCount++;
					}

					adapter.notifyDataSetChanged();
					int total = entries.size();
					tvAuditCount.setText(total + " entries  (" + flaggedCount + " flagged)");
				})
				.addOnFailureListener(e -> {
					// collectionGroup requires a Firestore index — show count anyway
					tvAuditCount.setText("Showing " + entries.size() + " audit entries");
				});
	}

	private String modeLabel(Map<String, Object> raw) {
		Object m = raw.get("transportMode");
		if (m != null) return m.toString();
		Object w = raw.get("wasteType");
		if (w != null) return w.toString();
		return "";
	}
}