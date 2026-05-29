package com.example.ecolums;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin System Health Monitor – live Firestore data.
 *
 * <p>What each metric means in terms of real data:</p>
 * <ul>
 *   <li><b>Server status / uptime</b> – result of a live Firestore write-ping.
 *       Uptime % is computed from the last 100 ping records stored in
 *       {@code systemHealth/pings/log}.</li>
 *   <li><b>App Activity chart</b> – count of activity-log documents submitted by
 *       all users in the selected window, bucketed by hour / day / week.</li>
 *   <li><b>Error Rate chart</b> – count of failed Firestore operations written to
 *       {@code systemHealth/errors/log}. Errors are written automatically whenever
 *       a Firestore call in this fragment fails.</li>
 * </ul>
 *
 * <p>AC coverage:</p>
 * <ul>
 *   <li>AC 1 – live server status + uptime %</li>
 *   <li>AC 2 – real activity bar chart</li>
 *   <li>AC 3 – real error bar chart + summary counters</li>
 *   <li>AC 4 – threshold warning banner (fires when peak > threshold)</li>
 *   <li>AC 5 – time-range filter: 24 h / 7 d / 1 month</li>
 * </ul>
 */
public class AdminSystemHealthFragment extends Fragment {

    // ── Thresholds: activity-log submissions per bucket ───────────────────
    // Index 0 = per-hour (24 h view), 1 = per-day (7 d), 2 = per-week (1 m)
    private static final float[] THRESHOLDS = { 30f, 100f, 400f };

    // ── State ─────────────────────────────────────────────────────────────
    private int currentRange = 0; // 0 = 24 h, 1 = 7 d, 2 = 1 m
    private Timestamp currentFrom;

    // ── Views ─────────────────────────────────────────────────────────────
    private MaterialButton btn24h, btn7d, btn1m;
    private LinearLayout  llWarning;
    private TextView      tvWarning;
    private TextView      tvTotalReq, tvTotalErr, tvErrRate, tvPeak;
    private TextView      tvOnlineLabel, tvUptimePct, tvStatusNote;
    private View          statusDot;
    private ProgressBar   pbUptime;
    private BarChart      apiChart, errChart;

    private FirebaseFirestore db;

    // ── Callback interface ────────────────────────────────────────────────
    private interface ListCallback {
        void onDone(List<Timestamp> timestamps);
    }

    // ── Fragment lifecycle ────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        db = FirebaseFirestore.getInstance();

        NestedScrollView scroll = new NestedScrollView(requireContext());
        scroll.setBackgroundColor(Color.parseColor("#F5F7F7"));

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(root);

        addTitle(root, "System Health Monitor");

        // ── AC 5: Time-range filter buttons ──────────────────────────────
        LinearLayout btnRow = new LinearLayout(requireContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dp(16));
        btnRow.setLayoutParams(rowLp);
        root.addView(btnRow);

        btn24h = makeRangeButton("Last 24 Hours");
        btn7d  = makeRangeButton("Last 7 Days");
        btn1m  = makeRangeButton("Last Month");
        addWeighted(btnRow, btn24h, 0);
        addWeighted(btnRow, btn7d,  dp(6));
        addWeighted(btnRow, btn1m,  dp(6));
        btn24h.setOnClickListener(v -> setRange(0));
        btn7d .setOnClickListener(v -> setRange(1));
        btn1m .setOnClickListener(v -> setRange(2));

        // ── AC 4: Threshold warning banner ────────────────────────────────
        llWarning = buildWarningBanner(root);

        // ── AC 1: Server status card ──────────────────────────────────────
        buildStatusCard(root);

        // ── AC 2: App activity chart ──────────────────────────────────────
        addSectionHeading(root, "App Activity (logs submitted)");
        apiChart = addBarChart(root);

        // ── AC 3: Error-rate chart ────────────────────────────────────────
        addSectionHeading(root, "Error Rate (failed operations)");
        errChart = addBarChart(root);

        buildErrorSummaryCard(root);

        // Kick off live checks
        pingFirestore();
        setRange(0);

        return scroll;
    }

    // ── Range switching ───────────────────────────────────────────────────

    private void setRange(int range) {
        currentRange = range;
        refreshButtonStyles();
        loadDataForRange();
    }

    private void refreshButtonStyles() {
        styleBtn(btn24h, currentRange == 0);
        styleBtn(btn7d,  currentRange == 1);
        styleBtn(btn1m,  currentRange == 2);
    }

    private void styleBtn(MaterialButton btn, boolean active) {
        btn.setBackgroundColor(active
                ? Color.parseColor("#009688") : Color.parseColor("#E8ECEC"));
        btn.setTextColor(active ? Color.WHITE : Color.parseColor("#607D8B"));
    }

    // ── Data loading ─────────────────────────────────────────────────────

    private void loadDataForRange() {
        // Clear charts while loading so stale data is not shown
        if (apiChart != null) { apiChart.clear(); apiChart.invalidate(); }
        if (errChart != null) { errChart.clear(); errChart.invalidate(); }

        Calendar calFrom = Calendar.getInstance();
        switch (currentRange) {
            case 1:  calFrom.add(Calendar.DAY_OF_YEAR, -7);  break;
            case 2:  calFrom.add(Calendar.MONTH, -1);         break;
            default: calFrom.add(Calendar.HOUR_OF_DAY, -24);  break;
        }
        currentFrom = new Timestamp(calFrom.getTimeInMillis() / 1000, 0);
        Timestamp to = Timestamp.now();

        fetchActivityLogs(currentFrom, to, activityTs ->
            fetchErrors(currentFrom, to, errorTs -> {
                if (getActivity() == null || !isAdded()) return;
                int buckets = bucketCount();
                float[] requests = bucket(activityTs, currentFrom, to, buckets);
                float[] errors   = bucket(errorTs,    currentFrom, to, buckets);
                String[] labels  = buildLabels(currentFrom);
                getActivity().runOnUiThread(() -> renderAll(requests, errors, labels));
            })
        );
    }

    /**
     * Fetches activity-log timestamps for every user in the time window.
     * Uses per-user subcollection queries, consistent with the pattern in
     * TeamDashboardActivity (no collection-group index required).
     */
    private void fetchActivityLogs(Timestamp from, Timestamp to, ListCallback callback) {
        db.collection("users").get()
                .addOnSuccessListener(userSnap -> {
                    if (userSnap.isEmpty()) {
                        callback.onDone(new ArrayList<>());
                        return;
                    }
                    List<Timestamp> results  = new ArrayList<>();
                    final int[]     remaining = { userSnap.size() };

                    for (var userDoc : userSnap.getDocuments()) {
                        db.collection("users").document(userDoc.getId())
                                .collection("activityLogs")
                                .whereGreaterThanOrEqualTo("date", from)
                                .whereLessThanOrEqualTo("date", to)
                                .get()
                                .addOnSuccessListener(logSnap -> {
                                    synchronized (results) {
                                        for (var doc : logSnap.getDocuments()) {
                                            Timestamp ts = doc.getTimestamp("date");
                                            if (ts != null) results.add(ts);
                                        }
                                    }
                                    if (--remaining[0] == 0) callback.onDone(results);
                                })
                                .addOnFailureListener(e -> {
                                    logError("activityLogs/" + userDoc.getId(), e.getMessage());
                                    if (--remaining[0] == 0) callback.onDone(results);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    logError("fetchUsers", e.getMessage());
                    callback.onDone(new ArrayList<>());
                });
    }

    /** Reads error-event timestamps from {@code systemHealth/errors/log}. */
    private void fetchErrors(Timestamp from, Timestamp to, ListCallback callback) {
        db.collection("systemHealth").document("errors").collection("log")
                .whereGreaterThanOrEqualTo("timestamp", from)
                .whereLessThanOrEqualTo("timestamp", to)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Timestamp> ts = new ArrayList<>();
                    for (var doc : snap.getDocuments()) {
                        Timestamp t = doc.getTimestamp("timestamp");
                        if (t != null) ts.add(t);
                    }
                    callback.onDone(ts);
                })
                .addOnFailureListener(e -> callback.onDone(new ArrayList<>()));
    }

    // ── Bucketing ────────────────────────────────────────────────────────

    private int bucketCount() {
        switch (currentRange) {
            case 1:  return 7;
            case 2:  return 4;
            default: return 24;
        }
    }

    /** Distributes timestamps evenly across {@code numBuckets} equal-width slots. */
    private float[] bucket(List<Timestamp> timestamps, Timestamp from,
                            Timestamp to, int numBuckets) {
        float[] counts = new float[numBuckets];
        long fromMs  = from.toDate().getTime();
        long toMs    = to.toDate().getTime();
        long spanMs  = Math.max(toMs - fromMs, 1);
        long bucketMs = spanMs / numBuckets;

        for (Timestamp ts : timestamps) {
            long ms  = ts.toDate().getTime();
            int  idx = (int) ((ms - fromMs) / bucketMs);
            idx = Math.max(0, Math.min(numBuckets - 1, idx));
            counts[idx]++;
        }
        return counts;
    }

    // ── Label builders ────────────────────────────────────────────────────

    /**
     * Builds axis labels derived from the real start of the time window,
     * so labels reflect actual hours / day-names rather than fixed strings.
     */
    private String[] buildLabels(Timestamp from) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(from.toDate().getTime());

        switch (currentRange) {
            case 1: {
                String[] dayNames = { "Sun","Mon","Tue","Wed","Thu","Fri","Sat" };
                String[] labels   = new String[7];
                for (int i = 0; i < 7; i++) {
                    labels[i] = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1];
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                }
                return labels;
            }
            case 2:
                return new String[]{ "Wk 1","Wk 2","Wk 3","Wk 4" };

            default: {
                String[] labels = new String[24];
                for (int i = 0; i < 24; i++) {
                    int h = cal.get(Calendar.HOUR_OF_DAY);
                    if      (h == 0)  labels[i] = "12am";
                    else if (h < 12)  labels[i] = h + "am";
                    else if (h == 12) labels[i] = "12pm";
                    else              labels[i] = (h - 12) + "pm";
                    cal.add(Calendar.HOUR_OF_DAY, 1);
                }
                return labels;
            }
        }
    }

    // ── Rendering ────────────────────────────────────────────────────────

    private void renderAll(float[] requests, float[] errors, String[] labels) {
        if (!isAdded()) return;

        float totalReq = 0, totalErr = 0, peak = 0;
        for (float r : requests) { totalReq += r; peak = Math.max(peak, r); }
        for (float e : errors)     totalErr += e;
        float errPct = totalReq > 0 ? (totalErr / totalReq) * 100f : 0f;

        // Metric tiles
        if (tvTotalReq != null) tvTotalReq.setText(
                String.format(Locale.getDefault(), "%.0f", totalReq));
        if (tvTotalErr != null) tvTotalErr.setText(
                String.format(Locale.getDefault(), "%.0f", totalErr));
        if (tvErrRate  != null) tvErrRate.setText(
                String.format(Locale.getDefault(), "%.2f%%", errPct));
        if (tvPeak     != null) tvPeak.setText(
                String.format(Locale.getDefault(), "%.0f", peak));

        // AC 4 – warning banner
        boolean overThreshold = peak > THRESHOLDS[currentRange];
        llWarning.setVisibility(overThreshold ? View.VISIBLE : View.GONE);
        if (tvWarning != null) {
            String unit = currentRange == 0 ? "logs/hr"
                        : currentRange == 1 ? "logs/day" : "logs/wk";
            tvWarning.setText(String.format(Locale.getDefault(),
                    "⚠  Peak load (%.0f %s) exceeds the safe threshold of %.0f %s. "
                            + "Consider scaling infrastructure.",
                    peak, unit, THRESHOLDS[currentRange], unit));
        }

        renderBarChart(apiChart, requests, labels, Color.parseColor("#3B82F6"));
        renderBarChart(errChart, errors,   labels, Color.parseColor("#EF4444"));
    }

    private void renderBarChart(BarChart chart, float[] values,
                                 String[] labels, int color) {
        if (chart == null) return;
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < values.length; i++) entries.add(new BarEntry(i, values[i]));

        BarDataSet ds = new BarDataSet(entries, "");
        ds.setColor(color);
        ds.setDrawValues(false);

        BarData data = new BarData(ds);
        data.setBarWidth(0.65f);
        chart.setData(data);
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        int maxLabels = currentRange == 0 ? 8 : labels.length;
        chart.getXAxis().setLabelCount(Math.min(labels.length, maxLabels), false);
        chart.animateY(350);
        chart.invalidate();
    }

    // ── Server status / ping (AC 1) ───────────────────────────────────────

    /** Writes a ping document to Firestore and measures round-trip latency. */
    private void pingFirestore() {
        long startMs = System.currentTimeMillis();

        Map<String, Object> pingDoc = new HashMap<>();
        pingDoc.put("timestamp", Timestamp.now());
        pingDoc.put("success",   false); // updated to true on success

        db.collection("systemHealth").document("pings").collection("log")
                .add(pingDoc)
                .addOnSuccessListener(ref -> {
                    long latencyMs = System.currentTimeMillis() - startMs;
                    ref.update("latencyMs", latencyMs, "success", true);

                    if (getActivity() != null && isAdded()) {
                        getActivity().runOnUiThread(() -> setStatusOnline(latencyMs));
                    }
                    computeUptime();
                })
                .addOnFailureListener(e -> {
                    if (getActivity() != null && isAdded()) {
                        getActivity().runOnUiThread(this::setStatusOffline);
                    }
                });
    }

    private void setStatusOnline(long latencyMs) {
        if (tvOnlineLabel == null) return;
        if (statusDot != null)    statusDot.setBackgroundColor(Color.parseColor("#22C55E"));
        tvOnlineLabel.setText("Online");
        tvOnlineLabel.setTextColor(Color.parseColor("#22C55E"));
        if (tvStatusNote != null) {
            tvStatusNote.setText(String.format(Locale.getDefault(),
                    "Latency: %d ms  •  Firestore reachable", latencyMs));
        }
    }

    private void setStatusOffline() {
        if (tvOnlineLabel == null) return;
        if (statusDot != null) statusDot.setBackgroundColor(Color.parseColor("#EF4444"));
        tvOnlineLabel.setText("Offline");
        tvOnlineLabel.setTextColor(Color.parseColor("#EF4444"));
        if (tvStatusNote != null) tvStatusNote.setText(
                "Cannot reach Firestore. Check connection.");
    }

    /**
     * Reads the last 100 ping records to compute uptime as
     * (successful pings / total pings) × 100.
     */
    private void computeUptime() {
        db.collection("systemHealth").document("pings").collection("log")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty() || getActivity() == null || !isAdded()) return;
                    long successful = 0;
                    for (var doc : snap.getDocuments()) {
                        Boolean ok = doc.getBoolean("success");
                        if (ok != null && ok) successful++;
                    }
                    float uptime = (successful * 100f) / snap.size();
                    getActivity().runOnUiThread(() -> updateUptimeBar(uptime));
                });
    }

    private void updateUptimeBar(float uptimePct) {
        if (pbUptime    != null) pbUptime.setProgress((int) (uptimePct * 10));
        if (tvUptimePct != null) tvUptimePct.setText(
                String.format(Locale.getDefault(), "%.1f%% uptime", uptimePct));
    }

    /**
     * Writes a record to {@code systemHealth/errors/log} so it shows up
     * in the Error Rate chart on future dashboard loads.
     */
    private void logError(String source, String message) {
        if (db == null) return;
        Map<String, Object> err = new HashMap<>();
        err.put("timestamp", Timestamp.now());
        err.put("source",    source);
        err.put("message",   message != null ? message : "unknown");
        db.collection("systemHealth").document("errors").collection("log").add(err);
    }

    // ── Card builders ─────────────────────────────────────────────────────

    /** AC 1 – server status + uptime progress bar (all values populated live). */
    private void buildStatusCard(LinearLayout parent) {
        CardView card  = makeCard(parent);
        LinearLayout inner = makePaddedVBox(card, dp(16));

        addSmallLabel(inner, "Server Status");

        // Status indicator row
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, dp(8), 0, dp(10));
        row.setLayoutParams(rowLp);

        // Coloured dot – starts amber ("checking"), updated by ping callback
        statusDot = new View(requireContext());
        statusDot.setBackgroundColor(Color.parseColor("#FFA000"));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(14), dp(14));
        dotLp.setMargins(0, 0, dp(10), 0);
        statusDot.setLayoutParams(dotLp);

        tvOnlineLabel = new TextView(requireContext());
        tvOnlineLabel.setText("Checking…");
        tvOnlineLabel.setTextSize(22);
        tvOnlineLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvOnlineLabel.setTextColor(Color.parseColor("#FFA000"));
        tvOnlineLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        tvUptimePct = new TextView(requireContext());
        tvUptimePct.setText("—");
        tvUptimePct.setTextSize(14);
        tvUptimePct.setTextColor(Color.parseColor("#607D8B"));
        tvUptimePct.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        row.addView(statusDot);
        row.addView(tvOnlineLabel);
        row.addView(tvUptimePct);
        inner.addView(row);

        // Uptime progress bar (0–100 % of last 100 pings)
        pbUptime = new ProgressBar(requireContext(), null,
                android.R.attr.progressBarStyleHorizontal);
        pbUptime.setMax(1000);
        pbUptime.setProgress(0);
        pbUptime.setProgressTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor("#22C55E")));
        pbUptime.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor("#E0E0E0")));
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(10));
        pbLp.setMargins(0, 0, 0, dp(8));
        pbUptime.setLayoutParams(pbLp);
        inner.addView(pbUptime);

        tvStatusNote = new TextView(requireContext());
        tvStatusNote.setText("Pinging Firestore…");
        tvStatusNote.setTextSize(11);
        tvStatusNote.setTextColor(Color.parseColor("#9E9E9E"));
        inner.addView(tvStatusNote);
    }

    /** AC 3 – summary tiles; values are filled in by renderAll(). */
    private void buildErrorSummaryCard(LinearLayout parent) {
        CardView card  = makeCard(parent);
        LinearLayout inner = makePaddedVBox(card, dp(16));
        addSmallLabel(inner, "Request Summary");

        LinearLayout tilesRow = new LinearLayout(requireContext());
        tilesRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        trLp.setMargins(0, dp(10), 0, dp(8));
        tilesRow.setLayoutParams(trLp);

        tvTotalReq = addMetricTile(tilesRow, "Total Logs", "—", dp(2));
        tvTotalErr = addMetricTile(tilesRow, "Errors",     "—", dp(4));
        tvErrRate  = addMetricTile(tilesRow, "Error Rate", "—", dp(4));
        tvPeak     = addMetricTile(tilesRow, "Peak",       "—", dp(4));
        inner.addView(tilesRow);

        TextView hint = new TextView(requireContext());
        hint.setText("Activity submissions and failed Firestore operations over the selected period.");
        hint.setTextSize(11);
        hint.setTextColor(Color.parseColor("#9E9E9E"));
        inner.addView(hint);
    }

    /** AC 4 – amber warning banner, hidden until peak exceeds threshold. */
    private LinearLayout buildWarningBanner(LinearLayout parent) {
        LinearLayout banner = new LinearLayout(requireContext());
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setBackgroundColor(Color.parseColor("#FFF3CD"));
        banner.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(14));
        banner.setLayoutParams(lp);
        banner.setVisibility(View.GONE);

        tvWarning = new TextView(requireContext());
        tvWarning.setTextSize(13);
        tvWarning.setTextColor(Color.parseColor("#856404"));
        tvWarning.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        banner.addView(tvWarning);

        parent.addView(banner);
        return banner;
    }

    // ── View helpers ──────────────────────────────────────────────────────

    private BarChart addBarChart(LinearLayout parent) {
        BarChart chart = new BarChart(requireContext());
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDoubleTapToZoomEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setDrawGridLines(false);
        chart.getXAxis().setTextColor(Color.parseColor("#999999"));
        chart.getXAxis().setTextSize(9f);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setTextColor(Color.parseColor("#999999"));
        chart.getAxisLeft().setGridColor(Color.parseColor("#22000000"));
        chart.setFitBars(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(200));
        lp.setMargins(0, dp(4), 0, dp(16));
        chart.setLayoutParams(lp);
        parent.addView(chart);
        return chart;
    }

    private void addTitle(LinearLayout parent, String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(18);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.parseColor("#212121"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(16));
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }

    private void addSectionHeading(LinearLayout parent, String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.parseColor("#424242"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(2));
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }

    private void addSmallLabel(LinearLayout parent, String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTextColor(Color.parseColor("#757575"));
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setAllCaps(true);
        tv.setLetterSpacing(0.06f);
        parent.addView(tv);
    }

    private TextView addMetricTile(LinearLayout row, String label,
                                    String value, int startMargin) {
        LinearLayout tile = new LinearLayout(requireContext());
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(8), dp(8), dp(8), dp(8));
        tile.setBackgroundColor(Color.parseColor("#F8FAFB"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(startMargin, 0, 0, 0);
        tile.setLayoutParams(lp);

        TextView tvLabel = new TextView(requireContext());
        tvLabel.setText(label);
        tvLabel.setTextSize(10);
        tvLabel.setTextColor(Color.parseColor("#607D8B"));
        tvLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextView tvValue = new TextView(requireContext());
        tvValue.setText(value);
        tvValue.setTextSize(15);
        tvValue.setTextColor(Color.parseColor("#212121"));
        tvValue.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvValue.setPadding(0, dp(4), 0, 0);

        tile.addView(tvLabel);
        tile.addView(tvValue);
        row.addView(tile);
        return tvValue;
    }

    private MaterialButton makeRangeButton(String label) {
        MaterialButton btn = new MaterialButton(requireContext());
        btn.setText(label);
        btn.setTextSize(11);
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        btn.setCornerRadius(dp(8));
        return btn;
    }

    private void addWeighted(LinearLayout row, View child, int startMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(startMargin, 0, 0, 0);
        child.setLayoutParams(lp);
        row.addView(child);
    }

    private CardView makeCard(LinearLayout parent) {
        CardView card = new CardView(requireContext());
        card.setRadius(dp(12));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(16));
        card.setLayoutParams(lp);
        parent.addView(card);
        return card;
    }

    private LinearLayout makePaddedVBox(CardView parent, int padding) {
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(padding, padding, padding, padding);
        parent.addView(inner);
        return inner;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
