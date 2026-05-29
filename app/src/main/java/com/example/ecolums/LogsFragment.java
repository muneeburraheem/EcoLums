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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Displays all activity logs for the current user with a summary header
 * showing total CO₂ saved, total points, and log count.
 */
public class LogsFragment extends Fragment {

	private RecyclerView rvLogs;
	private TextView tvTotalCo2, tvTotalPoints, tvLogCount, tvEmptyLogs;
	private SwipeRefreshLayout swipeRefresh;
	private ActivityLogAdapter adapter;
	private final List<ActivityLog> logList = new ArrayList<>();
	private FirebaseFirestore db;

	@Nullable
	@Override
	public View onCreateView(
			@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState
	) {
		View view = inflater.inflate(R.layout.fragment_logs, container, false);

		db = FirebaseFirestore.getInstance();

		rvLogs = view.findViewById(R.id.rv_logs);
		tvTotalCo2 = view.findViewById(R.id.tv_total_co2);
		tvTotalPoints = view.findViewById(R.id.tv_total_points);
		tvLogCount = view.findViewById(R.id.tv_log_count);
		tvEmptyLogs = view.findViewById(R.id.tv_empty_logs);
		swipeRefresh = view.findViewById(R.id.swipe_refresh);

		adapter = new ActivityLogAdapter(logList);
		rvLogs.setLayoutManager(new LinearLayoutManager(getContext()));
		rvLogs.setAdapter(adapter);

		swipeRefresh.setOnRefreshListener(this::loadLogs);
		swipeRefresh.setColorSchemeResources(R.color.teal_primary);

		loadLogs();
		return view;
	}

	private void loadLogs() {
		User user = UserSession.getInstance().getCurrentUser();
		if (user == null) return;

		db.collection("users")
				.document(user.getUserId())
				.collection("activityLogs")
				.orderBy("date", Query.Direction.DESCENDING)
				.get()
				.addOnSuccessListener(querySnapshot -> {
					logList.clear();
					double totalCo2 = 0;
					double totalPoints = 0;

					for (var doc : querySnapshot.getDocuments()) {
						ActivityLog log = doc.toObject(ActivityLog.class);
						if (log != null) {
							log.setLogId(doc.getId());
							logList.add(log);
							totalCo2 += log.getCo2EquivalentKg();
							totalPoints += log.getPointsEarned();
						}
					}

					adapter.notifyDataSetChanged();
					swipeRefresh.setRefreshing(false);

					tvTotalCo2.setText(String.format(Locale.getDefault(), "%.1f kg", totalCo2));
					tvTotalPoints.setText(String.format(Locale.getDefault(), "%.0f", totalPoints));
					tvLogCount.setText(String.valueOf(logList.size()));

					tvEmptyLogs.setVisibility(logList.isEmpty() ? View.VISIBLE : View.GONE);
					rvLogs.setVisibility(logList.isEmpty() ? View.GONE : View.VISIBLE);
				})
				.addOnFailureListener(e -> swipeRefresh.setRefreshing(false));
	}
}
