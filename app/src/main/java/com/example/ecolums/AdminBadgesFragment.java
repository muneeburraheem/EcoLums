package com.example.ecolums;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

/** Admin tab showing all badge definitions with ability to create/seed badges. */
public class AdminBadgesFragment extends Fragment {

    private LinearLayout llBadgeList;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_badges, container, false);
        db = FirebaseFirestore.getInstance();

        llBadgeList = view.findViewById(R.id.ll_badge_list);

        view.findViewById(R.id.btn_create_badge).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), CreateBadgeActivity.class)));

        view.findViewById(R.id.btn_seed_defaults).setOnClickListener(v -> seedDefaultBadges());

        loadBadges();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadBadges();
    }

    private void loadBadges() {
        if (llBadgeList == null) return;
        db.collection("badges").get().addOnSuccessListener(snap -> {
            if (!isAdded()) return;
            llBadgeList.removeAllViews();
            if (snap.isEmpty()) {
                TextView empty = new TextView(getContext());
                empty.setText("No badges yet. Seed defaults or create one.");
                empty.setTextColor(Color.parseColor("#757575"));
                empty.setPadding(0, 16, 0, 16);
                llBadgeList.addView(empty);
                return;
            }
            for (var doc : snap.getDocuments()) {
                Badge badge = doc.toObject(Badge.class);
                if (badge == null) continue;
                if (badge.getBadgeId() == null) badge.setBadgeId(doc.getId());
                llBadgeList.addView(buildBadgeRow(badge));
            }
        });
    }

    private View buildBadgeRow(Badge badge) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 12, 0, 12);

        // Emoji
        TextView tvEmoji = new TextView(getContext());
        tvEmoji.setText(badge.getDisplayEmoji());
        tvEmoji.setTextSize(24);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44));
        ep.setMarginEnd(dpToPx(12));
        tvEmoji.setLayoutParams(ep);
        tvEmoji.setGravity(Gravity.CENTER);

        // Info column
        LinearLayout info = new LinearLayout(getContext());
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(getContext());
        tvName.setText(badge.getName());
        tvName.setTextSize(14);
        tvName.setTextColor(Color.parseColor("#212121"));
        tvName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextView tvMeta = new TextView(getContext());
        String type = badge.getBadgeType() != null ? badge.getBadgeType() : "—";
        String tier = badge.getBadgeTier() != null ? badge.getBadgeTier() : "—";
        tvMeta.setText(type + " · " + tier);
        tvMeta.setTextSize(11);
        tvMeta.setTextColor(Color.parseColor("#009688"));

        info.addView(tvName);
        info.addView(tvMeta);

        // Divider wrapper
        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        row.addView(tvEmoji);
        row.addView(info);

        View divider = new View(getContext());
        divider.setBackgroundColor(Color.parseColor("#F0F0F0"));
        wrapper.addView(row);
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        return wrapper;
    }

    private void seedDefaultBadges() {
        List<Badge> defaults = BadgeDefinitions.getHardcodedBadges();
        final int[] pending = { defaults.size() };
        final int[] seeded = { 0 };

        for (Badge badge : defaults) {
            db.collection("badges").document(badge.getBadgeId())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (!doc.exists()) {
                            // Write badge to Firestore
                            java.util.Map<String, Object> data = new java.util.HashMap<>();
                            data.put("badgeId", badge.getBadgeId());
                            data.put("name", badge.getName());
                            data.put("description", badge.getDescription());
                            data.put("criteria", badge.getCriteria());
                            data.put("iconUrl", badge.getIconUrl());
                            data.put("badgeType", badge.getBadgeType());
                            data.put("badgeTier", badge.getBadgeTier());
                            data.put("pointThreshold", badge.getPointThreshold());
                            data.put("streakRequired", badge.getStreakRequired());
                            data.put("activityCountRequired", badge.getActivityCountRequired());
                            data.put("activityType", badge.getActivityType());
                            data.put("isHardcoded", true);
                            data.put("status", Badge.STATUS_UNEARNED);
                            data.put("createdAt", Timestamp.now());

                            db.collection("badges").document(badge.getBadgeId()).set(data)
                                    .addOnSuccessListener(v2 -> {
                                        seeded[0]++;
                                        checkDone(pending, seeded);
                                    })
                                    .addOnFailureListener(e -> checkDone(pending, seeded));
                        } else {
                            checkDone(pending, seeded);
                        }
                    });
        }
    }

    private void checkDone(int[] pending, int[] seeded) {
        pending[0]--;
        if (pending[0] <= 0) {
            if (isAdded() && getContext() != null) {
                Toast.makeText(getContext(),
                        seeded[0] + " default badge(s) seeded!", Toast.LENGTH_SHORT).show();
                loadBadges();
            }
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
