package com.example.ecolums;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bottom sheet that shows all flagged activity logs for a single user.
 *
 * <p>Launched from {@link AdminAnomalyFragment} when an admin taps a user row.
 * Queries {@code users/{userId}/activityLogs} filtered by {@code isFlagged == true}
 * and renders each entry via {@link FlaggedLogAdapter}.</p>
 */
public class FlaggedLogsBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_USER_ID   = "userId";
    private static final String ARG_USER_NAME = "userName";

    public static FlaggedLogsBottomSheet newInstance(String userId, String userName) {
        FlaggedLogsBottomSheet sheet = new FlaggedLogsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_USER_ID,   userId);
        args.putString(ARG_USER_NAME, userName);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_flagged_logs, container, false);

        Bundle args   = getArguments();
        String userId   = args != null ? args.getString(ARG_USER_ID,   "") : "";
        String userName = args != null ? args.getString(ARG_USER_NAME, "") : "";

        TextView tvTitle = view.findViewById(R.id.tv_flagged_title);
        TextView tvEmpty = view.findViewById(R.id.tv_flagged_empty);
        RecyclerView rv  = view.findViewById(R.id.rv_flagged_logs);

        tvTitle.setText("Flagged logs — " + userName);

        List<Map<String, Object>> logs = new ArrayList<>();
        FlaggedLogAdapter adapter = new FlaggedLogAdapter(logs);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);

        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("activityLogs")
                .whereEqualTo("isFlagged", true)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (var doc : snapshot.getDocuments()) {
                        Map<String, Object> d = doc.getData();
                        if (d != null) logs.add(d);
                    }
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(logs.isEmpty() ? View.VISIBLE : View.GONE);
                    if (!logs.isEmpty()) {
                        tvTitle.setText("Flagged logs — " + userName
                                + "  (" + logs.size() + ")");
                    }
                })
                .addOnFailureListener(e -> {
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("Failed to load: " + e.getMessage());
                });

        return view;
    }
}
