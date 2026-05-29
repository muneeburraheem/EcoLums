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

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin tab listing users with flagged activity logs.
 *
 * <p>Shows users sorted by number of flagged entries. Tapping a row opens
 * {@link FlaggedLogsBottomSheet} with the specific flagged logs for that user.</p>
 *
 * <p>Queries the {@code activityLogs} collection group filtered by
 * {@code isFlagged == true}. Requires the composite Firestore index defined
 * in {@code firestore.indexes.json}.</p>
 */
public class AdminAnomalyFragment extends Fragment {

    private RecyclerView rvUsers;
    private TextView tvSummary;
    private AnomalyUserAdapter adapter;
    private final List<AnomalyUserAdapter.UserSummary> summaries = new ArrayList<>();
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_anomaly, container, false);
        db = FirebaseFirestore.getInstance();

        rvUsers   = view.findViewById(R.id.rv_anomaly_users);
        tvSummary = view.findViewById(R.id.tv_anomaly_summary);

        adapter = new AnomalyUserAdapter(summaries, this::onUserClicked);
        rvUsers.setLayoutManager(new LinearLayoutManager(getContext()));
        rvUsers.setAdapter(adapter);

        view.findViewById(R.id.btn_refresh_anomaly)
                .setOnClickListener(v -> loadAnomalousUsers());

        loadAnomalousUsers();
        return view;
    }

    // -------------------------------------------------------------------------
    // Data loading
    // -------------------------------------------------------------------------

    private void loadAnomalousUsers() {
        summaries.clear();
        adapter.notifyDataSetChanged();
        tvSummary.setText("Loading…");

        db.collectionGroup("activityLogs")
                .whereEqualTo("isFlagged", true)
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(200)
                .get()
                .addOnSuccessListener(snapshot -> {
                    // Group flagged logs by userId
                    Map<String, List<Map<String, Object>>> byUser = new HashMap<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Map<String, Object> data = doc.getData();
                        if (data == null) continue;
                        String uid = (String) data.get("userId");
                        if (uid == null || uid.isEmpty()) continue;
                        byUser.computeIfAbsent(uid, k -> new ArrayList<>()).add(data);
                    }

                    if (byUser.isEmpty()) {
                        tvSummary.setText("No flagged activities found.");
                        return;
                    }

                    tvSummary.setText(snapshot.size() + " flagged log(s) across "
                            + byUser.size() + " user(s) — tap a row to inspect");

                    // For each user, compute max anomaly score and fetch display name
                    for (Map.Entry<String, List<Map<String, Object>>> entry : byUser.entrySet()) {
                        String uid  = entry.getKey();
                        List<Map<String, Object>> logs = entry.getValue();

                        double maxScore = 0;
                        for (Map<String, Object> log : logs) {
                            Object s = log.get("anomalyScore");
                            if (s instanceof Number) {
                                maxScore = Math.max(maxScore, ((Number) s).doubleValue());
                            }
                        }
                        final double finalMax   = maxScore;
                        final int    count      = logs.size();

                        db.collection("users").document(uid).get()
                                .addOnSuccessListener(userDoc -> {
                                    String name = uid; // fallback to UID if name unavailable
                                    if (userDoc.exists()) {
                                        String n = userDoc.getString("name");
                                        if (n != null && !n.isEmpty()) name = n;
                                    }
                                    summaries.add(new AnomalyUserAdapter.UserSummary(
                                            uid, name, count, finalMax));
                                    // Keep sorted: most flagged first, then highest score
                                    summaries.sort((a, b) -> {
                                        int cmp = Integer.compare(b.flaggedCount, a.flaggedCount);
                                        return cmp != 0 ? cmp : Double.compare(b.maxScore, a.maxScore);
                                    });
                                    adapter.notifyDataSetChanged();
                                });
                    }
                })
                .addOnFailureListener(e ->
                        tvSummary.setText("Failed to load — Firestore index may be missing.\n"
                                + e.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private void onUserClicked(AnomalyUserAdapter.UserSummary user) {
        FlaggedLogsBottomSheet sheet =
                FlaggedLogsBottomSheet.newInstance(user.userId, user.displayName);
        sheet.show(getChildFragmentManager(), "flagged_logs");
    }
}
