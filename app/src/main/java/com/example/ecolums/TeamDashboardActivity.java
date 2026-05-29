package com.example.ecolums;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Team Dashboard showing club performance in active challenges.
 * Displays:
 *  - Club's aggregate score progress bar per challenge
 *  - Live inter-club comparison bars (ranked, colour-coded)
 *  - Top contributors within the club
 */
public class TeamDashboardActivity extends AppCompatActivity {

    public static final String EXTRA_CLUB_ID = "club_id";

    private LinearLayout llChallenges;
    private LinearLayout llClubAnalytics;
    private TextView tvClubName, tvMemberCount;
    private FirebaseFirestore db;
    private ProgressTracker progressTracker;
    private Club loadedClub;
    private String clubId;
    private String lastAnalyticsCsv = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_team_dashboard);

        db = FirebaseFirestore.getInstance();
        progressTracker = new ProgressTracker();
        clubId = getIntent().getStringExtra(EXTRA_CLUB_ID);
        if (clubId == null) { finish(); return; }

        findViewById(R.id.tv_back).setOnClickListener(v -> finish());
        tvClubName   = findViewById(R.id.tv_club_name_dash);
        tvMemberCount = findViewById(R.id.tv_member_count_dash);
        llClubAnalytics = findViewById(R.id.ll_club_analytics);
        llChallenges  = findViewById(R.id.ll_team_challenges);

        loadClubAndChallenges();
    }

    private void loadClubAndChallenges() {
        db.collection("clubs").document(clubId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { finish(); return; }
                    Club club = doc.toObject(Club.class);
                    if (club == null) { finish(); return; }
                    club.setClubId(doc.getId());
                    loadedClub = club;

                    tvClubName.setText(club.getName() != null ? club.getName() : "Club");
                    int mc = club.getMemberIds() != null ? club.getMemberIds().size() : 0;
                    tvMemberCount.setText(mc + " members");

                    loadClubAnalytics(club);
                    loadActiveChallenges(club);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load club.", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void loadClubAnalytics(Club club) {
        llClubAnalytics.removeAllViews();
        CardView card = new CardView(this);
        card.setRadius(dpToPx(12));
        card.setCardElevation(dpToPx(3));
        card.setCardBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dpToPx(16));
        card.setLayoutParams(cardLp);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        TextView title = new TextView(this);
        title.setText("Club Analytics");
        title.setTextSize(16);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#212121"));
        inner.addView(title);

        // AC 1 (Club Analytics story): time-period selector — Weekly / Monthly / Challenge
        LinearLayout periodRow = new LinearLayout(this);
        periodRow.setOrientation(LinearLayout.HORIZONTAL);
        periodRow.setPadding(0, dpToPx(6), 0, dpToPx(10));
        inner.addView(periodRow);

        MaterialButton btnWeekly   = makeSmallToggle("Weekly");
        MaterialButton btnMonthly  = makeSmallToggle("Monthly");
        addWeightedBtn(periodRow, btnWeekly, 0);
        addWeightedBtn(periodRow, btnMonthly, dpToPx(6));
        activateToggle(btnWeekly, true);
        activateToggle(btnMonthly, false);

        TextView subtitle = new TextView(this);
        subtitle.setText("Monthly aggregate impact from member logs");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.parseColor("#757575"));
        subtitle.setPadding(0, dpToPx(2), 0, dpToPx(12));
        inner.addView(subtitle);

        LinearLayout metricsRow = new LinearLayout(this);
        metricsRow.setOrientation(LinearLayout.HORIZONTAL);
        inner.addView(metricsRow);
        TextView co2 = addMetricTile(metricsRow, "CO₂ Saved", "...");
        TextView waste = addMetricTile(metricsRow, "Waste", "...");
        TextView energy = addMetricTile(metricsRow, "Energy", "...");
        TextView km = addMetricTile(metricsRow, "Green Km", "...");

        TextView participation = new TextView(this);
        participation.setText("Participation: loading...");
        participation.setTextSize(13);
        participation.setTextColor(Color.parseColor("#00695C"));
        participation.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        participation.setPadding(0, dpToPx(12), 0, dpToPx(4));
        inner.addView(participation);

        LinearLayout breakdown = new LinearLayout(this);
        breakdown.setOrientation(LinearLayout.VERTICAL);
        inner.addView(breakdown);

        // AC 2 (Team Challenges story): Enroll Entire Team button for club captain / admin
        User currentUser = UserSession.getInstance().getCurrentUser();
        boolean isLeaderOrAdmin = currentUser != null && (
                User.ROLE_CLUB_LEADER.equals(currentUser.getRole())
                || User.ROLE_ADMIN.equals(currentUser.getRole()));
        if (isLeaderOrAdmin) {
            MaterialButton enrollBtn = new MaterialButton(this);
            enrollBtn.setText("Enroll Team in Challenge");
            enrollBtn.setTextColor(Color.WHITE);
            enrollBtn.setBackgroundColor(Color.parseColor("#3B82F6"));
            LinearLayout.LayoutParams enrollLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(46));
            enrollLp.setMargins(0, dpToPx(12), 0, 0);
            enrollBtn.setLayoutParams(enrollLp);
            enrollBtn.setOnClickListener(v -> showEnrollTeamDialog(club));
            inner.addView(enrollBtn);
        }

        MaterialButton export = new MaterialButton(this);
        export.setText("Export Club CSV");
        export.setTextColor(Color.WHITE);
        export.setBackgroundColor(Color.parseColor("#009688"));
        LinearLayout.LayoutParams exportLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(46));
        exportLp.setMargins(0, dpToPx(8), 0, 0);
        export.setLayoutParams(exportLp);
        export.setOnClickListener(v -> exportClubCsv());
        inner.addView(export);

        card.addView(inner);
        llClubAnalytics.addView(card);

        Timestamp to = Timestamp.now();
        Calendar calMonthly = Calendar.getInstance();
        calMonthly.add(Calendar.MONTH, -1);
        Timestamp fromMonthly = new Timestamp(calMonthly.getTimeInMillis() / 1000, 0);

        Calendar calWeekly = Calendar.getInstance();
        calWeekly.add(Calendar.DAY_OF_YEAR, -7);
        Timestamp fromWeekly = new Timestamp(calWeekly.getTimeInMillis() / 1000, 0);

        // Wire period toggle buttons to reload analytics
        Timestamp from = fromMonthly; // default
        btnWeekly.setOnClickListener(v -> {
            activateToggle(btnWeekly, true);
            activateToggle(btnMonthly, false);
            subtitle.setText("Weekly aggregate impact from member logs");
            reloadAnalytics(club, fromWeekly, to, co2, waste, energy, km, participation, breakdown);
        });
        btnMonthly.setOnClickListener(v -> {
            activateToggle(btnWeekly, false);
            activateToggle(btnMonthly, true);
            subtitle.setText("Monthly aggregate impact from member logs");
            reloadAnalytics(club, fromMonthly, to, co2, waste, energy, km, participation, breakdown);
        });

        progressTracker.getClubImpactSummary(clubId, from, to, summary -> runOnUiThread(() -> {
            ProgressTracker.ImpactSummary s = summary != null ? summary : new ProgressTracker.ImpactSummary();
            co2.setText(String.format(Locale.getDefault(), "%.1f kg", s.totalCO2SavedKg));
            waste.setText(String.format(Locale.getDefault(), "%.1f kg", s.totalWasteDivertedKg));
            energy.setText(String.format(Locale.getDefault(), "%.1f kWh", s.totalEnergySavedKwh));
            loadGreenKilometers(club, from, to, value -> runOnUiThread(() -> {
                km.setText(String.format(Locale.getDefault(), "%.1f km", value));
                lastAnalyticsCsv = "Metric,Value\n"
                        + "CO2 Saved (kg)," + s.totalCO2SavedKg + "\n"
                        + "Waste Diverted (kg)," + s.totalWasteDivertedKg + "\n"
                        + "Energy Saved (kWh)," + s.totalEnergySavedKwh + "\n"
                        + "Green Kilometers," + value + "\n"
                        + "Points," + s.totalPointsEarned + "\n";
            }));
        }));

        progressTracker.getClubParticipationRate(clubId, rate -> runOnUiThread(() ->
                participation.setText(String.format(Locale.getDefault(),
                        "Participation: %.0f%% of members logged this week", rate * 100))));

        loadCategoryBreakdown(club, from, to, breakdown);
    }

    private TextView addMetricTile(LinearLayout row, String label, String value) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        tile.setBackgroundColor(Color.parseColor("#F4FBF9"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dpToPx(3), 0, dpToPx(3), 0);
        tile.setLayoutParams(lp);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(10);
        tvLabel.setTextColor(Color.parseColor("#607D8B"));
        tvLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(15);
        tvValue.setTextColor(Color.parseColor("#212121"));
        tvValue.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvValue.setPadding(0, dpToPx(6), 0, 0);

        tile.addView(tvLabel);
        tile.addView(tvValue);
        row.addView(tile);
        return tvValue;
    }

    private interface DoubleCallback { void onValue(double value); }

    private void loadGreenKilometers(Club club, Timestamp from, Timestamp to, DoubleCallback callback) {
        List<String> memberIds = club.getMemberIds() != null ? club.getMemberIds() : new ArrayList<>();
        if (memberIds.isEmpty()) { callback.onValue(0); return; }
        final double[] total = { 0 };
        final int[] remaining = { memberIds.size() };
        for (String uid : memberIds) {
            db.collection("users").document(uid).collection("activityLogs")
                    .whereGreaterThanOrEqualTo("date", from)
                    .whereLessThanOrEqualTo("date", to)
                    .whereEqualTo("category", ActivityLog.CATEGORY_TRANSPORT)
                    .get()
                    .addOnSuccessListener(snap -> {
                        for (var doc : snap.getDocuments()) {
                            String mode = doc.getString("transportMode");
                            Double dist = doc.getDouble("distanceKm");
                            if (dist != null && !ActivityLog.TRANSPORT_CAR.equals(mode)) total[0] += dist;
                        }
                        if (--remaining[0] == 0) callback.onValue(total[0]);
                    })
                    .addOnFailureListener(e -> {
                        if (--remaining[0] == 0) callback.onValue(total[0]);
                    });
        }
    }

    private void loadCategoryBreakdown(Club club, Timestamp from, Timestamp to, LinearLayout container) {
        List<String> memberIds = club.getMemberIds() != null ? club.getMemberIds() : new ArrayList<>();
        if (memberIds.isEmpty()) return;
        Map<String, Double> values = new HashMap<>();
        final int[] remaining = { memberIds.size() };
        for (String uid : memberIds) {
            db.collection("users").document(uid).collection("activityLogs")
                    .whereGreaterThanOrEqualTo("date", from)
                    .whereLessThanOrEqualTo("date", to)
                    .get()
                    .addOnSuccessListener(snap -> {
                        for (var doc : snap.getDocuments()) {
                            String cat = doc.getString("category");
                            Double co2 = doc.getDouble("co2EquivalentKg");
                            if (cat != null && co2 != null) {
                                values.put(cat, values.getOrDefault(cat, 0.0) + Math.max(0, co2));
                            }
                        }
                        if (--remaining[0] == 0) renderCategoryBreakdown(values, container);
                    })
                    .addOnFailureListener(e -> {
                        if (--remaining[0] == 0) renderCategoryBreakdown(values, container);
                    });
        }
    }

    private void renderCategoryBreakdown(Map<String, Double> values, LinearLayout container) {
        runOnUiThread(() -> {
            container.removeAllViews();
            addSectionLabel(container, "Category Breakdown");
            double max = 1;
            for (double v : values.values()) max = Math.max(max, v);
            addProgressRow(container, "Transport", values.getOrDefault(ActivityLog.CATEGORY_TRANSPORT, 0.0),
                    max, "kg CO₂", (int) ((values.getOrDefault(ActivityLog.CATEGORY_TRANSPORT, 0.0) / max) * 100),
                    Color.parseColor("#F97316"), false);
            addProgressRow(container, "Energy", values.getOrDefault(ActivityLog.CATEGORY_ENERGY, 0.0),
                    max, "kg CO₂", (int) ((values.getOrDefault(ActivityLog.CATEGORY_ENERGY, 0.0) / max) * 100),
                    Color.parseColor("#3B82F6"), false);
            addProgressRow(container, "Waste", values.getOrDefault(ActivityLog.CATEGORY_WASTE, 0.0),
                    max, "kg CO₂", (int) ((values.getOrDefault(ActivityLog.CATEGORY_WASTE, 0.0) / max) * 100),
                    Color.parseColor("#A78BFA"), false);
        });
    }

    private void exportClubCsv() {
        try {
            if (lastAnalyticsCsv == null || lastAnalyticsCsv.isEmpty()) {
                Toast.makeText(this, "Analytics are still loading.", Toast.LENGTH_SHORT).show();
                return;
            }
            File file = new File(getExternalFilesDir(null), "club_analytics_" + clubId + ".csv");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(lastAnalyticsCsv.getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this, "Report saved: " + file.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadActiveChallenges(Club club) {
        List<String> memberIds = club.getMemberIds() != null ? club.getMemberIds() : new ArrayList<>();

        db.collection("challenges")
                .whereEqualTo("status", Challenge.STATUS_ACTIVE)
                .get()
                .addOnSuccessListener(snap -> {
                    llChallenges.removeAllViews();
                    boolean found = false;

                    for (var doc : snap.getDocuments()) {
                        Challenge c = doc.toObject(Challenge.class);
                        if (c == null) continue;
                        c.setChallengeId(doc.getId());

                        // Compute club's aggregate score from member IDs
                        Map<String, Double> scores = c.getParticipantScores();
                        if (scores == null) continue;

                        double clubScore = 0;
                        for (String memberId : memberIds) {
                            Double s = scores.get(memberId);
                            if (s != null) clubScore += s;
                        }

                        if (clubScore == 0 && !hasClubMembers(scores, memberIds)) continue;
                        found = true;
                        llChallenges.addView(buildChallengeCard(c, clubScore, memberIds));
                    }

                    if (!found) {
                        TextView tv = new TextView(this);
                        tv.setText("Your club hasn't participated in any active challenges yet.");
                        tv.setTextColor(Color.parseColor("#757575"));
                        tv.setGravity(Gravity.CENTER);
                        tv.setPadding(24, 48, 24, 24);
                        llChallenges.addView(tv);
                    }
                });
    }

    private boolean hasClubMembers(Map<String, Double> scores, List<String> memberIds) {
        for (String id : memberIds) {
            if (scores.containsKey(id)) return true;
        }
        return false;
    }

    private LinearLayout buildChallengeCard(Challenge c, double clubScore, List<String> memberIds) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dpToPx(16));
        container.setLayoutParams(lp);

        // Challenge header card
        CardView card = new CardView(this);
        CardView.LayoutParams clp = new CardView.LayoutParams(
                CardView.LayoutParams.MATCH_PARENT, CardView.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(clp);
        card.setRadius(dpToPx(12));
        card.setCardElevation(dpToPx(3));
        card.setCardBackgroundColor(Color.WHITE);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        // Title row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvIcon = new TextView(this);
        tvIcon.setText(goalEmoji(c.getGoalType()));
        tvIcon.setTextSize(22);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dpToPx(36), dpToPx(36));
        ip.setMarginEnd(dpToPx(10));
        tvIcon.setLayoutParams(ip);
        tvIcon.setGravity(Gravity.CENTER);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(c.getTitle() != null ? c.getTitle() : "Challenge");
        tvTitle.setTextSize(15);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(Color.parseColor("#212121"));

        String unit = c.getTargetUnit() != null ? c.getTargetUnit() : "";
        TextView tvTarget = new TextView(this);
        tvTarget.setText(String.format(Locale.getDefault(), "Target: %.0f %s", c.getTargetValue(), unit));
        tvTarget.setTextSize(12);
        tvTarget.setTextColor(Color.parseColor("#757575"));

        titleCol.addView(tvTitle);
        titleCol.addView(tvTarget);
        titleRow.addView(tvIcon);
        titleRow.addView(titleCol);
        inner.addView(titleRow);

        // Club progress bar
        addSectionLabel(inner, "Your Club's Progress");
        double target = c.getTargetValue();
        int clubPct = target > 0 ? (int) Math.min(100, (clubScore / target) * 100) : 0;
        addProgressRow(inner, loadedClub.getName() != null ? loadedClub.getName() : "Your Club",
                clubScore, target, unit, clubPct, Color.parseColor("#009688"), true);

        // Load inter-club leaderboard
        LinearLayout interClub = new LinearLayout(this);
        interClub.setOrientation(LinearLayout.VERTICAL);
        addSectionLabel(inner, "Club Rankings");
        inner.addView(interClub);

        // Top contributors
        LinearLayout topContrib = new LinearLayout(this);
        topContrib.setOrientation(LinearLayout.VERTICAL);
        addSectionLabel(inner, "Top Contributors");
        inner.addView(topContrib);

        card.addView(inner);
        container.addView(card);

        // Load inter-club comparison async
        double perCapitaScore = memberIds.isEmpty() ? clubScore : clubScore / memberIds.size();
        loadInterClubRanking(c, perCapitaScore, memberIds, interClub);
        loadTopContributors(c, memberIds, topContrib);

        return container;
    }

    private void loadInterClubRanking(Challenge c, double myClubScore,
                                       List<String> myMemberIds, LinearLayout container) {
        db.collection("clubs").get()
                .addOnSuccessListener(snap -> {
                    // Build list of (clubName, aggregateScore)
                    List<String[]> clubScores = new ArrayList<>();
                    Map<String, Double> pScores = c.getParticipantScores();
                    if (pScores == null) return;

                    final int[] remaining = { snap.size() };
                    if (remaining[0] == 0) return;

                    for (var doc : snap.getDocuments()) {
                        Club club = doc.toObject(Club.class);
                        if (club == null || club.getMemberIds() == null) {
                            remaining[0]--;
                            if (remaining[0] == 0) renderInterClub(clubScores, myClubScore, container);
                            continue;
                        }
                        club.setClubId(doc.getId());

                        double score = 0;
                        for (String mid : club.getMemberIds()) {
                            Double s = pScores.get(mid);
                            if (s != null) score += s;
                        }
                        if (!club.getMemberIds().isEmpty()) {
                            score = score / club.getMemberIds().size();
                        }
                        if (score > 0 || doc.getId().equals(clubId)) {
                            clubScores.add(new String[]{
                                    club.getName() != null ? club.getName() : doc.getId(),
                                    String.valueOf(score),
                                    doc.getId()
                            });
                        }
                        remaining[0]--;
                        if (remaining[0] == 0) renderInterClub(clubScores, myClubScore, container);
                    }
                });
    }

    private void renderInterClub(List<String[]> clubScores, double myClubScore,
                                  LinearLayout container) {
        runOnUiThread(() -> {
            container.removeAllViews();
            // Sort descending by score
            clubScores.sort((a, b) -> Double.compare(
                    Double.parseDouble(b[1]), Double.parseDouble(a[1])));

            double maxScore = clubScores.isEmpty() ? 1
                    : Math.max(1, Double.parseDouble(clubScores.get(0)[1]));

            String[] medals = { "🥇", "🥈", "🥉" };
            int myRank = -1;
            double nextScore = 0;
            for (int i = 0; i < clubScores.size(); i++) {
                String[] entry = clubScores.get(i);
                String name  = entry[0];
                double score = Double.parseDouble(entry[1]);
                boolean isMe = entry[2].equals(clubId);
                if (isMe) {
                    myRank = i;
                    if (i > 0) nextScore = Double.parseDouble(clubScores.get(i - 1)[1]);
                }
                int pct = (int) ((score / maxScore) * 100);

                String medal = i < 3 ? medals[i] : String.valueOf(i + 1);
                int barColor = isMe ? Color.parseColor("#009688")
                        : (i == 0 ? Color.parseColor("#FFB300") : Color.parseColor("#BDBDBD"));
                String labelName = (isMe ? "★ " : "") + name;
                addProgressRow(container, medal + " " + labelName, score, maxScore,
                        "", pct, barColor, isMe);
            }
            if (myRank > 0) {
                double gap = Math.max(0, nextScore - myClubScore + 1);
                TextView tvGap = new TextView(this);
                tvGap.setText(String.format(Locale.getDefault(),
                        "Gap analysis: %.0f more units needed to overtake the next club.", gap));
                tvGap.setTextSize(12);
                tvGap.setTextColor(Color.parseColor("#00695C"));
                tvGap.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                tvGap.setPadding(0, dpToPx(6), 0, dpToPx(2));
                container.addView(tvGap);
            }
        });
    }

    private void loadTopContributors(Challenge c, List<String> memberIds,
                                      LinearLayout container) {
        Map<String, Double> scores = c.getParticipantScores();
        if (scores == null || memberIds.isEmpty()) return;

        // Sort members by their score descending
        List<Map.Entry<String, Double>> sorted = new ArrayList<>();
        for (String mid : memberIds) {
            Double s = scores.get(mid);
            if (s != null && s > 0) sorted.add(Map.entry(mid, s));
        }
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        int limit = Math.min(5, sorted.size());
        final int[] remaining = { limit };
        if (limit == 0) {
            runOnUiThread(() -> {
                TextView tv = new TextView(this);
                tv.setText("No contributions yet.");
                tv.setTextSize(12);
                tv.setTextColor(Color.parseColor("#9E9E9E"));
                container.addView(tv);
            });
            return;
        }

        String unit = c.getTargetUnit() != null ? c.getTargetUnit() : "pts";

        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Double> entry = sorted.get(i);
            final int rank = i + 1;
            final double score = entry.getValue();

            db.collection("users").document(entry.getKey()).get()
                    .addOnSuccessListener(doc -> {
                        String name = doc.exists() && doc.getString("name") != null
                                ? doc.getString("name") : entry.getKey();
                        runOnUiThread(() -> {
                            LinearLayout row = new LinearLayout(this);
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            row.setGravity(Gravity.CENTER_VERTICAL);
                            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT);
                            rlp.setMargins(0, dpToPx(4), 0, dpToPx(4));
                            row.setLayoutParams(rlp);

                            String[] m = { "🥇", "🥈", "🥉", "4.", "5." };
                            TextView tvRank = new TextView(this);
                            tvRank.setText(rank <= 3 ? m[rank - 1] : m[rank - 1]);
                            tvRank.setTextSize(16);
                            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dpToPx(32), dpToPx(32));
                            rp.setMarginEnd(dpToPx(8));
                            tvRank.setLayoutParams(rp);

                            TextView tvName = new TextView(this);
                            tvName.setText(name);
                            tvName.setTextSize(13);
                            tvName.setTextColor(Color.parseColor("#212121"));
                            tvName.setLayoutParams(new LinearLayout.LayoutParams(0,
                                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                            TextView tvScore = new TextView(this);
                            tvScore.setText(String.format(Locale.getDefault(), "%.1f %s", score, unit));
                            tvScore.setTextSize(13);
                            tvScore.setTextColor(Color.parseColor("#009688"));
                            tvScore.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

                            row.addView(tvRank);
                            row.addView(tvName);
                            row.addView(tvScore);
                            container.addView(row);
                        });
                        remaining[0]--;
                    })
                    .addOnFailureListener(e -> remaining[0]--);
        }
    }

    private void addSectionLabel(LinearLayout parent, String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(12);
        tv.setTextColor(Color.parseColor("#757575"));
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setAllCaps(true);
        tv.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dpToPx(14), 0, dpToPx(6));
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }

    private void addProgressRow(LinearLayout parent, String name, double score,
                                 double maxScore, String unit, int pct, int barColor,
                                 boolean highlight) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, 0, 0, dpToPx(8));
        row.setLayoutParams(rlp);
        if (highlight) {
            row.setBackgroundColor(Color.parseColor("#E0F7FA"));
            row.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
        }

        // Name + score label row
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextSize(13);
        tvName.setTextColor(highlight ? Color.parseColor("#00695C") : Color.parseColor("#424242"));
        if (highlight) tvName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvScore = new TextView(this);
        tvScore.setText(String.format(Locale.getDefault(),
                unit.isEmpty() ? "%.0f pts" : "%.1f " + unit, score));
        tvScore.setTextSize(12);
        tvScore.setTextColor(barColor);
        tvScore.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        labelRow.addView(tvName);
        labelRow.addView(tvScore);

        // Progress bar
        ProgressBar pb = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgress(pct);
        pb.setProgressTintList(android.content.res.ColorStateList.valueOf(barColor));
        pb.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#E0E0E0")));
        LinearLayout.LayoutParams pblp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(8));
        pblp.setMargins(0, dpToPx(4), 0, 0);
        pb.setLayoutParams(pblp);

        row.addView(labelRow);
        row.addView(pb);
        parent.addView(row);
    }

    /**
     * AC 2 (Team Challenges story): shows a dialog listing active challenges so the
     * team captain can bulk-enrol all club members in one tap.
     */
    private void showEnrollTeamDialog(Club club) {
        db.collection("challenges")
                .whereEqualTo("status", Challenge.STATUS_ACTIVE)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Challenge> active = new ArrayList<>();
                    for (var doc : snap.getDocuments()) {
                        Challenge c = doc.toObject(Challenge.class);
                        if (c != null) {
                            c.setChallengeId(doc.getId());
                            if (!c.isManualDeactivated()) active.add(c);
                        }
                    }
                    if (active.isEmpty()) {
                        Toast.makeText(this, "No active challenges available.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String[] names = new String[active.size()];
                    for (int i = 0; i < active.size(); i++) {
                        names[i] = active.get(i).getTitle() != null
                                ? active.get(i).getTitle() : "Challenge " + (i + 1);
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Enroll Entire Team")
                            .setItems(names, (dialog, which) ->
                                    enrollAllMembersInChallenge(club, active.get(which)))
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load challenges.", Toast.LENGTH_SHORT).show());
    }

    /**
     * Bulk-adds every club member to the selected challenge's participantIds list.
     */
    private void enrollAllMembersInChallenge(Club club, Challenge challenge) {
        List<String> memberIds = club.getMemberIds() != null ? club.getMemberIds() : new ArrayList<>();
        if (memberIds.isEmpty()) {
            Toast.makeText(this, "Club has no members.", Toast.LENGTH_SHORT).show();
            return;
        }
        db.collection("challenges").document(challenge.getChallengeId())
                .update("participantIds", FieldValue.arrayUnion(memberIds.toArray()))
                .addOnSuccessListener(v -> {
                    // Also seed participantScores entries so the leaderboard shows them
                    Map<String, Object> scores = new HashMap<>();
                    for (String uid : memberIds) {
                        scores.put("participantScores." + uid, 0.0);
                    }
                    db.collection("challenges").document(challenge.getChallengeId())
                            .update(scores);
                    Toast.makeText(this,
                            memberIds.size() + " members enrolled in \""
                                    + challenge.getTitle() + "\"",
                            Toast.LENGTH_SHORT).show();
                    loadActiveChallenges(club);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Enrollment failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    /**
     * Reloads analytics metrics for the given time window without rebuilding the card.
     */
    private void reloadAnalytics(Club club, Timestamp from, Timestamp to,
                                  TextView co2, TextView waste, TextView energy, TextView km,
                                  TextView participation, LinearLayout breakdown) {
        co2.setText("...");
        waste.setText("...");
        energy.setText("...");
        km.setText("...");
        progressTracker.getClubImpactSummary(club.getClubId(), from, to, summary ->
                runOnUiThread(() -> {
                    ProgressTracker.ImpactSummary s =
                            summary != null ? summary : new ProgressTracker.ImpactSummary();
                    co2.setText(String.format(Locale.getDefault(), "%.1f kg", s.totalCO2SavedKg));
                    waste.setText(String.format(Locale.getDefault(), "%.1f kg", s.totalWasteDivertedKg));
                    energy.setText(String.format(Locale.getDefault(), "%.1f kWh", s.totalEnergySavedKwh));
                    loadGreenKilometers(club, from, to, value ->
                            runOnUiThread(() ->
                                    km.setText(String.format(Locale.getDefault(), "%.1f km", value))));
                    lastAnalyticsCsv = "Metric,Value\n"
                            + "CO2 Saved (kg)," + s.totalCO2SavedKg + "\n"
                            + "Waste Diverted (kg)," + s.totalWasteDivertedKg + "\n"
                            + "Energy Saved (kWh)," + s.totalEnergySavedKwh + "\n"
                            + "Points," + s.totalPointsEarned + "\n";
                }));
        progressTracker.getClubParticipationRate(club.getClubId(), rate ->
                runOnUiThread(() -> participation.setText(String.format(Locale.getDefault(),
                        "Participation: %.0f%% of members logged this week", rate * 100))));
        loadCategoryBreakdown(club, from, to, breakdown);
    }

    /** Helper to style a toggle button active/inactive. */
    private void activateToggle(MaterialButton btn, boolean active) {
        btn.setBackgroundColor(active
                ? Color.parseColor("#009688") : Color.parseColor("#E8ECEC"));
        btn.setTextColor(active ? Color.WHITE : Color.parseColor("#607D8B"));
    }

    private MaterialButton makeSmallToggle(String label) {
        MaterialButton btn = new MaterialButton(this);
        btn.setText(label);
        btn.setTextSize(12);
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        btn.setCornerRadius(dpToPx(8));
        return btn;
    }

    private void addWeightedBtn(LinearLayout row, MaterialButton btn, int startMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(startMargin, 0, 0, 0);
        btn.setLayoutParams(lp);
        row.addView(btn);
    }

    private String goalEmoji(String goalType) {
        if (goalType == null) return "🌍";
        switch (goalType) {
            case Challenge.GOAL_TYPE_TRANSPORT: return "🚴";
            case Challenge.GOAL_TYPE_ENERGY:    return "⚡";
            case Challenge.GOAL_TYPE_WASTE:     return "♻";
            default:                            return "🌍";
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
