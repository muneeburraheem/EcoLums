package com.example.ecolums;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Displays the user's Trophy Case — a grid of all badges (earned in colour,
 * unearned in greyscale). Tapping a badge shows its detail modal.
 */
public class TrophyCaseActivity extends AppCompatActivity {

    private RecyclerView rvBadges;
    private TextView tvEarnedCount, tvTotalCount;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trophy_case);

        db = FirebaseFirestore.getInstance();
        findViewById(R.id.tv_back).setOnClickListener(v -> finish());

        rvBadges = findViewById(R.id.rv_badges);
        tvEarnedCount = findViewById(R.id.tv_earned_count);
        tvTotalCount  = findViewById(R.id.tv_total_count);

        rvBadges.setLayoutManager(new GridLayoutManager(this, 2));

        loadBadges();
    }

    private void loadBadges() {
        User user = UserSession.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }

        // Step 1: Load user's earned badge sub-collection
        db.collection("users").document(user.getUserId())
                .collection("badges")
                .get()
                .addOnSuccessListener(earnedSnap -> {
                    Map<String, Badge> earnedMap = new HashMap<>();
                    for (var doc : earnedSnap.getDocuments()) {
                        Badge b = doc.toObject(Badge.class);
                        if (b != null) {
                            b.setStatus(Badge.STATUS_EARNED);
                            earnedMap.put(b.getBadgeId(), b);
                        }
                    }

                    // Step 2: Load all global badge definitions
                    db.collection("badges").get()
                            .addOnSuccessListener(allSnap -> {
                                List<Badge> displayList = new ArrayList<>();

                                for (var doc : allSnap.getDocuments()) {
                                    Badge b = doc.toObject(Badge.class);
                                    if (b == null) continue;
                                    if (b.getBadgeId() == null) b.setBadgeId(doc.getId());

                                    if (earnedMap.containsKey(b.getBadgeId())) {
                                        // Use the earned copy (has dateEarned)
                                        displayList.add(earnedMap.get(b.getBadgeId()));
                                    } else {
                                        b.setStatus(Badge.STATUS_UNEARNED);
                                        displayList.add(b);
                                    }
                                }

                                // Sort: earned first, then premium, then alphabetical
                                displayList.sort(Comparator
                                        .<Badge, Integer>comparing(b -> b.isEarned() ? 0 : 1)
                                        .thenComparing(b -> b.isPremium() ? 0 : 1)
                                        .thenComparing(b -> b.getName() != null ? b.getName() : ""));

                                long earnedCount = displayList.stream().filter(Badge::isEarned).count();
                                tvEarnedCount.setText(String.valueOf(earnedCount));
                                tvTotalCount.setText(String.valueOf(displayList.size()));

                                BadgeAdapter adapter = new BadgeAdapter(displayList, this::showBadgeDetail);
                                rvBadges.setAdapter(adapter);
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed to load badges.", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load your badges.", Toast.LENGTH_SHORT).show());
    }

    private void showBadgeDetail(Badge badge) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_badge_detail, null);

        TextView tvEmoji  = v.findViewById(R.id.tv_detail_badge_emoji);
        TextView tvTier   = v.findViewById(R.id.tv_detail_badge_tier);
        TextView tvName   = v.findViewById(R.id.tv_detail_badge_name);
        TextView tvDesc   = v.findViewById(R.id.tv_detail_badge_desc);
        TextView tvCrit   = v.findViewById(R.id.tv_detail_badge_criteria);
        TextView tvDate   = v.findViewById(R.id.tv_detail_badge_date);

        tvEmoji.setText(badge.getDisplayEmoji());
        tvName.setText(badge.getName());
        tvDesc.setText(badge.getDescription());
        tvCrit.setText("Criteria: " + (badge.getCriteria() != null ? badge.getCriteria() : "—"));

        if (badge.isPremium()) {
            tvTier.setText("✨ PREMIUM");
            tvTier.setTextColor(0xFFFF8F00);
        } else {
            tvTier.setText("STARTER");
            tvTier.setTextColor(0xFF757575);
        }

        if (badge.isEarned() && badge.getDateEarned() != null) {
            Date d = badge.getDateEarned().toDate();
            tvDate.setText("Earned: " + new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(d));
            tvDate.setVisibility(View.VISIBLE);
        } else {
            tvDate.setText("Not yet earned");
            tvDate.setTextColor(0xFFBDBDBD);
        }

        new AlertDialog.Builder(this)
                .setView(v)
                .setPositiveButton("Close", null)
                .show();
    }

    /**
     * Shows a celebratory badge-earned modal. Call this from any Activity.
     */
    public static void showBadgeEarnedModal(FragmentActivity activity, Badge badge) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            View v = LayoutInflater.from(activity).inflate(R.layout.dialog_badge_earned, null);
            TextView tvEmoji = v.findViewById(R.id.tv_earned_badge_emoji);
            TextView tvName  = v.findViewById(R.id.tv_earned_badge_name);
            TextView tvDesc  = v.findViewById(R.id.tv_earned_badge_desc);

            tvEmoji.setText(badge.getDisplayEmoji());
            tvName.setText(badge.getName());
            tvDesc.setText(badge.getDescription());

            new AlertDialog.Builder(activity)
                    .setView(v)
                    .setPositiveButton("Awesome!", null)
                    .setCancelable(false)
                    .show();
        });
    }
}
