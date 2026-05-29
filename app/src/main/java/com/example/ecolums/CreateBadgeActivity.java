package com.example.ecolums;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin screen for creating a new badge of any type:
 * Point Threshold, Streak, Activity Count, Challenge Completion, Challenge Percentage.
 */
public class CreateBadgeActivity extends AppCompatActivity {

    private TextInputEditText etName, etDesc, etCriteria, etEmoji;
    private TextInputEditText etPointThreshold, etStreakDays, etActivityCount, etChallengePercent;
    private Spinner spinnerType, spinnerTier, spinnerActivityType, spinnerChallenge;
    private LinearLayout llPointFields, llStreakFields, llActivityCountFields, llChallengeFields;
    private FirebaseFirestore db;

    private List<String> challengeIds = new ArrayList<>();
    private List<String> challengeNames = new ArrayList<>();
    private String selectedChallengeId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_badge);

        db = FirebaseFirestore.getInstance();
        findViewById(R.id.tv_back).setOnClickListener(v -> finish());

        etName            = findViewById(R.id.et_badge_name);
        etDesc            = findViewById(R.id.et_badge_desc);
        etCriteria        = findViewById(R.id.et_badge_criteria);
        etEmoji           = findViewById(R.id.et_badge_emoji);
        etPointThreshold  = findViewById(R.id.et_point_threshold);
        etStreakDays      = findViewById(R.id.et_streak_days);
        etActivityCount   = findViewById(R.id.et_activity_count);
        etChallengePercent = findViewById(R.id.et_challenge_percent);

        spinnerType         = findViewById(R.id.spinner_badge_type);
        spinnerTier         = findViewById(R.id.spinner_badge_tier);
        spinnerActivityType = findViewById(R.id.spinner_activity_type);
        spinnerChallenge    = findViewById(R.id.spinner_challenge);

        llPointFields        = findViewById(R.id.ll_point_fields);
        llStreakFields       = findViewById(R.id.ll_streak_fields);
        llActivityCountFields = findViewById(R.id.ll_activity_count_fields);
        llChallengeFields    = findViewById(R.id.ll_challenge_fields);

        setupSpinners();
        loadChallenges();

        MaterialButton btnCreate = findViewById(R.id.btn_create_badge);
        btnCreate.setOnClickListener(v -> createBadge());
    }

    private void setupSpinners() {
        String[] types = {
                Badge.TYPE_POINT_THRESHOLD,
                Badge.TYPE_STREAK,
                Badge.TYPE_ACTIVITY_COUNT,
                Badge.TYPE_CHALLENGE_COMPLETION,
                Badge.TYPE_CHALLENGE_PERCENTAGE
        };
        String[] typeLabels = {
                "Point Threshold", "Streak", "Activity Count",
                "Challenge Completion", "Challenge Percentage"
        };
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, typeLabels);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(typeAdapter);

        spinnerType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                updateCriteriaFields(types[pos]);
            }
            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        });

        String[] tiers = { Badge.TIER_PREMIUM, Badge.TIER_BASIC };
        String[] tierLabels = { "Premium", "Basic (Starter)" };
        ArrayAdapter<String> tierAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, tierLabels);
        tierAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTier.setAdapter(tierAdapter);

        String[] activityTypes = { "Any", "BIKE", "BUS", "WALK", "TRAIN", "CAR", "RECYCLE", "COMPOST" };
        ArrayAdapter<String> actAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, activityTypes);
        actAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerActivityType.setAdapter(actAdapter);
    }

    private void updateCriteriaFields(String type) {
        llPointFields.setVisibility(Badge.TYPE_POINT_THRESHOLD.equals(type) ? View.VISIBLE : View.GONE);
        llStreakFields.setVisibility(Badge.TYPE_STREAK.equals(type) ? View.VISIBLE : View.GONE);
        llActivityCountFields.setVisibility(Badge.TYPE_ACTIVITY_COUNT.equals(type) ? View.VISIBLE : View.GONE);
        llChallengeFields.setVisibility(
                (Badge.TYPE_CHALLENGE_COMPLETION.equals(type) || Badge.TYPE_CHALLENGE_PERCENTAGE.equals(type))
                        ? View.VISIBLE : View.GONE);
        // Challenge percentage also shows percent field
        if (etChallengePercent != null) {
            etChallengePercent.setVisibility(
                    Badge.TYPE_CHALLENGE_PERCENTAGE.equals(type) ? View.VISIBLE : View.GONE);
        }
    }

    private void loadChallenges() {
        db.collection("challenges")
                .whereEqualTo("status", Challenge.STATUS_ACTIVE)
                .get()
                .addOnSuccessListener(snap -> {
                    challengeIds.clear();
                    challengeNames.clear();
                    challengeIds.add(null);
                    challengeNames.add("— Select Challenge —");
                    for (var doc : snap.getDocuments()) {
                        String title = doc.getString("title");
                        challengeIds.add(doc.getId());
                        challengeNames.add(title != null ? title : doc.getId());
                    }
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, challengeNames);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerChallenge.setAdapter(adapter);
                    spinnerChallenge.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                            selectedChallengeId = challengeIds.get(pos);
                        }
                        @Override
                        public void onNothingSelected(AdapterView<?> p) {}
                    });
                });
    }

    private void createBadge() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(this, "Enter a badge name.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] typeKeys = {
                Badge.TYPE_POINT_THRESHOLD, Badge.TYPE_STREAK, Badge.TYPE_ACTIVITY_COUNT,
                Badge.TYPE_CHALLENGE_COMPLETION, Badge.TYPE_CHALLENGE_PERCENTAGE
        };
        String[] tierKeys = { Badge.TIER_PREMIUM, Badge.TIER_BASIC };

        String badgeType = typeKeys[spinnerType.getSelectedItemPosition()];
        String badgeTier = tierKeys[spinnerTier.getSelectedItemPosition()];
        String emoji = etEmoji.getText() != null && !etEmoji.getText().toString().isEmpty()
                ? etEmoji.getText().toString() : "🌿";
        String desc     = etDesc.getText() != null ? etDesc.getText().toString().trim() : "";
        String criteria = etCriteria.getText() != null ? etCriteria.getText().toString().trim() : "";

        Map<String, Object> data = new HashMap<>();
        data.put("name",       name);
        data.put("description", desc);
        data.put("criteria",   criteria);
        data.put("iconUrl",    emoji);
        data.put("badgeType",  badgeType);
        data.put("badgeTier",  badgeTier);
        data.put("status",     Badge.STATUS_UNEARNED);
        data.put("isHardcoded", false);
        data.put("createdAt",  Timestamp.now());

        // Type-specific fields
        if (Badge.TYPE_POINT_THRESHOLD.equals(badgeType)) {
            int pts = parseIntSafe(etPointThreshold);
            data.put("pointThreshold", pts);
            data.put("criteria", criteria.isEmpty() ? "Earn " + pts + " green points" : criteria);
        } else if (Badge.TYPE_STREAK.equals(badgeType)) {
            int days = parseIntSafe(etStreakDays);
            data.put("streakRequired", days);
            data.put("pointThreshold", 0);
            data.put("criteria", criteria.isEmpty() ? "Maintain a " + days + "-day streak" : criteria);
        } else if (Badge.TYPE_ACTIVITY_COUNT.equals(badgeType)) {
            int count = parseIntSafe(etActivityCount);
            String[] actKeys = { null, "BIKE", "BUS", "WALK", "TRAIN", "CAR", "RECYCLE", "COMPOST" };
            String actType = actKeys[spinnerActivityType.getSelectedItemPosition()];
            data.put("activityCountRequired", count);
            data.put("activityType", actType);
            data.put("pointThreshold", 0);
        } else if (Badge.TYPE_CHALLENGE_COMPLETION.equals(badgeType)) {
            data.put("challengeId", selectedChallengeId);
            data.put("pointThreshold", 0);
        } else if (Badge.TYPE_CHALLENGE_PERCENTAGE.equals(badgeType)) {
            double pct = parseDoubleSafe(etChallengePercent);
            data.put("challengeId", selectedChallengeId);
            data.put("challengePercentage", pct);
            data.put("pointThreshold", 0);
        }

        db.collection("badges")
                .add(data)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Badge \"" + name + "\" created!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to save badge.", Toast.LENGTH_SHORT).show());
    }

    private int parseIntSafe(TextInputEditText et) {
        try {
            return Integer.parseInt(et.getText() != null ? et.getText().toString().trim() : "0");
        } catch (NumberFormatException e) { return 0; }
    }

    private double parseDoubleSafe(TextInputEditText et) {
        try {
            return Double.parseDouble(et.getText() != null ? et.getText().toString().trim() : "0");
        } catch (NumberFormatException e) { return 0; }
    }
}
