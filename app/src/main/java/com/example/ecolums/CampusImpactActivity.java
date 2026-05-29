package com.example.ecolums;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Campus-wide Impact Dashboard.
 * <p>
 * Shows hardcoded seed values (representing historical campus data) plus any
 * activity logs that have been recorded in Firestore this year. The seed values
 * are added to whatever real data Firestore returns, so every new log entry
 * immediately increases the displayed totals.
 * <p>
 * To remove the seed data later: set every SEED_* constant to 0 and clear the
 * SEED_MONTHLY array entries.
 * <p>
 * Firestore query: collectionGroup("activityLogs") with no filters — no index
 * required. Year filtering is done in Java.
 */
public class CampusImpactActivity extends AppCompatActivity {

	private static final String TAG = "CampusImpact";
	private static final float GOAL_TARGET = 10000f;

	// ── Seed baseline ──────────────────────────────────────────────────────
	// These represent historical campus data. Set all to 0 when ready to use
	// purely real-time data.

	private static final float SEED_CARBON = 1115f; // kg CO₂ total (goal progress + co2 card)
	private static final float SEED_WASTE_KG = 623f; // kg physical waste (waste card)
	private static final float SEED_ENERGY_KWH = 1387f; // kWh saved (energy card)

	// CO₂ split for the pie chart — must sum to SEED_CARBON
	private static final float SEED_TRANSPORT_CO2 = 550f;
	private static final float SEED_ENERGY_CO2 = 390f;
	private static final float SEED_WASTE_CO2 = 175f;

	// Monthly CO₂ breakdown [transport, energy, waste] — index 0=Jan … 11=Dec
	private static final float[][] SEED_MONTHLY = {
			{ 55f, 40f, 18f }, // Jan
			{ 50f, 35f, 15f }, // Feb
			{ 55f, 40f, 18f }, // Mar
			{ 45f, 32f, 15f }, // Apr
			{ 40f, 28f, 12f }, // May
			{ 35f, 25f, 10f }, // Jun
			{ 30f, 22f, 9f }, // Jul
			{ 40f, 28f, 12f }, // Aug
			{ 50f, 35f, 15f }, // Sep
			{ 55f, 40f, 18f }, // Oct
			{ 50f, 38f, 17f }, // Nov
			{ 45f, 27f, 16f }, // Dec
	};

	private static final String[] MONTHS = {
			"Jan", "Feb", "Mar", "Apr", "May", "Jun",
			"Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
	};

	// ── Views ──────────────────────────────────────────────────────────────

	private TextView tvCo2, tvWaste, tvEnergy, tvGoalProgress, tvGoalPct;
	private TextView tvTransportPct, tvEnergySavingPct, tvWasteDivertedPct;
	private LinearLayout llDepartmentBreakdown, llGoalsArchive;
	private ProgressBar pbGoal;
	private LineChart lineChart;
	private PieChart pieChart;

	private FirebaseFirestore db;
	private ProgressTracker progressTracker;
	private String latestCsv = "";

	// ── Lifecycle ──────────────────────────────────────────────────────────

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_campus_impact);

		db = FirebaseFirestore.getInstance();
		progressTracker = new ProgressTracker();

		tvCo2 = findViewById(R.id.tv_campus_co2);
		tvWaste = findViewById(R.id.tv_campus_waste);
		tvEnergy = findViewById(R.id.tv_campus_energy);
		pbGoal = findViewById(R.id.pb_campus_goal);
		tvGoalProgress = findViewById(R.id.tv_goal_progress);
		tvGoalPct = findViewById(R.id.tvGoalPct);
		tvTransportPct = findViewById(R.id.tvTransportPct);
		tvEnergySavingPct = findViewById(R.id.tvEnergySavingPct);
		tvWasteDivertedPct = findViewById(R.id.tvWasteDivertedPct);
		llDepartmentBreakdown = findViewById(R.id.ll_department_breakdown);
		llGoalsArchive = findViewById(R.id.ll_goals_archive);
		lineChart = findViewById(R.id.lineChart);
		pieChart = findViewById(R.id.pieChart);

		findViewById(R.id.btnBack).setOnClickListener(v -> finish());
		findViewById(R.id.btn_export_campus_csv).setOnClickListener(v -> exportCampusCsv());
		findViewById(R.id.btn_set_campus_goal).setOnClickListener(v -> showCreateGoalDialog());

		setupLineChartStyle();
		setupPieChartStyle();
	}

	@Override
	protected void onResume() {
		super.onResume();
		// Show seed values instantly, then fire the Firestore fetch
		renderCharts(
				SEED_CARBON, SEED_WASTE_KG, SEED_ENERGY_KWH,
				SEED_TRANSPORT_CO2, SEED_ENERGY_CO2, SEED_WASTE_CO2,
				buildSeedMonthlyMap()
		);
		fetchRealData();
		loadDepartmentBreakdown();
		loadGoalsArchive();
	}

	// ── Firestore ──────────────────────────────────────────────────────────

	/**
	 * Fetches all activityLog documents (no Firestore filter = no index needed),
	 * then filters to the current calendar year in memory. The real values are
	 * added on top of the seed baseline and the UI is re-rendered.
	 */
	private void fetchRealData() {
		int thisYear = Calendar.getInstance().get(Calendar.YEAR);

		db.collectionGroup("activityLogs")
				.get()
				.addOnSuccessListener(snapshot -> {
					float realCarbon = 0f;
					float realWasteKg = 0f;
					float realEnergyKwh = 0f;
					float realTransportCO2 = 0f;
					float realEnergyCO2 = 0f;
					float realWasteCO2 = 0f;

					Map<String, float[]> realMonthly = new HashMap<>();

					for (DocumentSnapshot doc : snapshot.getDocuments()) {
						// Filter to current year without needing a Firestore index
						Timestamp ts = doc.getTimestamp("date");
						if (ts == null) continue;

						Calendar cal = Calendar.getInstance();
						cal.setTimeInMillis(ts.getSeconds() * 1000L);
						if (cal.get(Calendar.YEAR) != thisYear) continue;

						String category = doc.getString("category");
						if (category == null) continue;

						double co2 = doc.getDouble("co2EquivalentKg") != null
								? doc.getDouble("co2EquivalentKg") : 0.0;
						String month = MONTHS[cal.get(Calendar.MONTH)];

						realCarbon += (float) co2;

						switch (category.toUpperCase(Locale.ROOT)) {
						case ActivityLog.CATEGORY_TRANSPORT:
							realTransportCO2 += (float) co2;
							getOrCreate(realMonthly, month)[0] += (float) co2;
							break;
						case ActivityLog.CATEGORY_ENERGY:
							realEnergyCO2 += (float) co2;
							Double eKwh = doc.getDouble("energyKwh");
							realEnergyKwh += eKwh != null ? eKwh.floatValue() : 0f;
							getOrCreate(realMonthly, month)[1] += (float) co2;
							break;
						case ActivityLog.CATEGORY_WASTE:
							realWasteCO2 += (float) co2;
							Double wKg = doc.getDouble("wasteKg");
							realWasteKg += wKg != null ? wKg.floatValue() : 0f;
							getOrCreate(realMonthly, month)[2] += (float) co2;
							break;
						}
					}

					// Merge seed + real
					final float totalCarbon = SEED_CARBON + realCarbon;
					final float totalWasteKg = SEED_WASTE_KG + realWasteKg;
					final float totalEnergyKwh = SEED_ENERGY_KWH + realEnergyKwh;
					final float totalTransport = SEED_TRANSPORT_CO2 + realTransportCO2;
					final float totalEnergyCO2 = SEED_ENERGY_CO2 + realEnergyCO2;
					final float totalWasteCO2 = SEED_WASTE_CO2 + realWasteCO2;

					// Merge monthly maps
					Map<String, float[]> mergedMonthly = buildSeedMonthlyMap();
					for (Map.Entry<String, float[]> entry : realMonthly.entrySet()) {
						float[] dst = getOrCreate(mergedMonthly, entry.getKey());
						float[] src = entry.getValue();
						dst[0] += src[0];
						dst[1] += src[1];
						dst[2] += src[2];
					}
					final Map<String, float[]> fMonthly = mergedMonthly;

					runOnUiThread(() -> renderCharts(
							totalCarbon, totalWasteKg, totalEnergyKwh,
							totalTransport, totalEnergyCO2, totalWasteCO2,
							fMonthly
					));
				})
				.addOnFailureListener(e -> Log.e(TAG, "Firestore fetch failed", e));
		// On failure, seed values already displayed — nothing to reset
	}

	// ── Render ─────────────────────────────────────────────────────────────

	/**
	 * Updates every widget on screen with the given aggregated values.
	 */
	private void renderCharts(
			float carbon, float wasteKg, float energyKwh,
			float transportCO2, float energyCO2, float wasteCO2,
			Map<String, float[]> monthly
	) {
		// Metric cards
		tvCo2.setText(String.format(Locale.getDefault(), "%.0f kg", carbon));
		tvWaste.setText(String.format(Locale.getDefault(), "%.0f kg", wasteKg));
		tvEnergy.setText(String.format(Locale.getDefault(), "%.0f kWh", energyKwh));
		latestCsv = "Metric,Value\n"
				+ "Total Carbon Offset (kg)," + carbon + "\n"
				+ "Total Waste Diverted (kg)," + wasteKg + "\n"
				+ "Total Energy Saved (kWh)," + energyKwh + "\n"
				+ "Transport CO2 (kg)," + transportCO2 + "\n"
				+ "Energy CO2 (kg)," + energyCO2 + "\n"
				+ "Waste CO2 (kg)," + wasteCO2 + "\n";

		// Goal banner
		int pct = Math.min(100, (int) ((carbon / GOAL_TARGET) * 100));
		pbGoal.setMax((int) GOAL_TARGET);
		pbGoal.setProgress((int) carbon);
		tvGoalProgress.setText(String.format(Locale.getDefault(), "%.0f kg saved", carbon));
		tvGoalPct.setText(pct + "% of goal");

		// Pie chart + % labels
		updatePieChart(transportCO2, energyCO2, wasteCO2);

		// Line chart
		updateLineChart(monthly);
	}

	// ── Line chart ─────────────────────────────────────────────────────────

	private void setupLineChartStyle() {
		lineChart.getDescription().setEnabled(false);
		lineChart.getLegend().setEnabled(false);
		lineChart.setTouchEnabled(true);
		lineChart.getAxisRight().setEnabled(false);
		lineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
		lineChart.getXAxis().setDrawGridLines(false);
		lineChart.getXAxis().setTextColor(Color.parseColor("#999999"));
		lineChart.getXAxis().setTextSize(10f);
		lineChart.getAxisLeft().setAxisMinimum(0f);
		lineChart.getAxisLeft().setTextColor(Color.parseColor("#999999"));
		lineChart.getAxisLeft().setGridColor(Color.parseColor("#22000000"));
		lineChart.setExtraBottomOffset(8f);
	}

	/**
	 * Draws three smooth lines (Transport, Energy, Waste) across Jan–Dec.
	 */
	private void updateLineChart(Map<String, float[]> monthly) {
		List<Entry> transportE = new ArrayList<>();
		List<Entry> energyE = new ArrayList<>();
		List<Entry> wasteE = new ArrayList<>();

		for (int i = 0; i < MONTHS.length; i++) {
			float[] v = monthly.containsKey(MONTHS[i])
					? monthly.get(MONTHS[i]) : new float[] { 0f, 0f, 0f };
			transportE.add(new Entry(i, v[0]));
			energyE.add(new Entry(i, v[1]));
			wasteE.add(new Entry(i, v[2]));
		}

		lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(MONTHS));
		lineChart.getXAxis().setLabelCount(MONTHS.length, true);

		LineDataSet dsTransport = makeLineSet(transportE, "#A78BFA", "#33A78BFA"); // purple
		LineDataSet dsEnergy = makeLineSet(energyE, "#3B82F6", "#333B82F6"); // blue
		LineDataSet dsWaste = makeLineSet(wasteE, "#F97316", "#33F97316"); // orange

		lineChart.setData(new LineData(dsTransport, dsEnergy, dsWaste));
		lineChart.animateX(600);
		lineChart.invalidate();
	}

	/**
	 * Creates a smooth filled LineDataSet with no circles or value labels.
	 *
	 * @param entries   data points
	 * @param lineColor hex line color
	 * @param fillColor hex fill color (semi-transparent)
	 */
	private LineDataSet makeLineSet(List<Entry> entries, String lineColor, String fillColor) {
		LineDataSet set = new LineDataSet(entries, "");
		set.setColor(Color.parseColor(lineColor));
		set.setFillColor(Color.parseColor(fillColor));
		set.setFillAlpha(80);
		set.setDrawFilled(true);
		set.setLineWidth(2.5f);
		set.setDrawCircles(false);
		set.setDrawValues(false);
		set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
		return set;
	}

	// ── Pie chart ──────────────────────────────────────────────────────────

	private void setupPieChartStyle() {
		pieChart.getDescription().setEnabled(false);
		pieChart.getLegend().setEnabled(false);
		pieChart.setTouchEnabled(false);
		pieChart.setDrawEntryLabels(false);
		pieChart.setHoleRadius(50f);
		pieChart.setTransparentCircleRadius(0f);
		pieChart.setHoleColor(Color.WHITE);
	}

	/**
	 * Draws the category donut chart and updates the % labels.
	 */
	private void updatePieChart(float transport, float energy, float waste) {
		float total = transport + energy + waste;
		if (total == 0) total = 1f;

		float tPct = (transport / total) * 100f;
		float ePct = (energy / total) * 100f;
		float wPct = (waste / total) * 100f;

		List<PieEntry> entries = new ArrayList<>();
		entries.add(new PieEntry(tPct));
		entries.add(new PieEntry(ePct));
		entries.add(new PieEntry(wPct));

		PieDataSet ds = new PieDataSet(entries, "");
		ds.setColors(
				Color.parseColor("#F97316"), // orange — transport
				Color.parseColor("#3B82F6"), // blue   — energy
				Color.parseColor("#A78BFA")  // purple — waste
		);
		ds.setSliceSpace(3f);
		ds.setDrawValues(false);

		pieChart.setData(new PieData(ds));
		pieChart.animateY(600);
		pieChart.invalidate();

		tvTransportPct.setText(String.format(Locale.getDefault(), "%.0f%%", tPct));
		tvEnergySavingPct.setText(String.format(Locale.getDefault(), "%.0f%%", ePct));
		tvWasteDivertedPct.setText(String.format(Locale.getDefault(), "%.0f%%", wPct));
	}

	// ── Helpers ────────────────────────────────────────────────────────────

	/**
	 * Builds a monthly map pre-loaded with the seed values.
	 */
	private Map<String, float[]> buildSeedMonthlyMap() {
		Map<String, float[]> map = new HashMap<>();
		for (int i = 0; i < MONTHS.length; i++) {
			map.put(MONTHS[i], SEED_MONTHLY[i].clone());
		}
		return map;
	}

	/**
	 * Returns the float[3] for a month key, creating it with zeros if absent.
	 */
	private float[] getOrCreate(Map<String, float[]> map, String key) {
		if (!map.containsKey(key)) map.put(key, new float[] { 0f, 0f, 0f });
		return map.get(key);
	}

	private void loadDepartmentBreakdown() {
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.DAY_OF_YEAR, 1);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		Timestamp from = new Timestamp(cal.getTimeInMillis() / 1000, 0);
		Timestamp to = Timestamp.now();

		progressTracker.getDepartmentalBreakdown(from, to, breakdown -> runOnUiThread(() -> {
			llDepartmentBreakdown.removeAllViews();
			if (breakdown.isEmpty()) {
				addSmallText(llDepartmentBreakdown, "No club or department activity yet.");
				return;
			}
			double max = Math.max(1, breakdown.get(0).summary.totalCO2SavedKg);
			int limit = Math.min(5, breakdown.size());
			for (int i = 0; i < limit; i++) {
				ProgressTracker.ClubBreakdownEntry entry = breakdown.get(i);
				addBreakdownRow(entry.clubName, entry.summary.totalCO2SavedKg,
						(int) ((entry.summary.totalCO2SavedKg / max) * 100));
			}
		}));
	}

	private void addBreakdownRow(String name, double co2, int pct) {
		TextView tv = new TextView(this);
		tv.setText(String.format(Locale.getDefault(), "%s  •  %.1f kg CO₂", name, co2));
		tv.setTextSize(13);
		tv.setTextColor(Color.parseColor("#212121"));
		tv.setPadding(0, dp(6), 0, dp(2));
		llDepartmentBreakdown.addView(tv);
		ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
		pb.setMax(100);
		pb.setProgress(pct);
		pb.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#009688")));
		pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E0E0E0")));
		llDepartmentBreakdown.addView(pb, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, dp(7)));
	}

	private void loadGoalsArchive() {
		db.collection("campusGoals").get()
				.addOnSuccessListener(snap -> {
					llGoalsArchive.removeAllViews();
					if (snap.isEmpty()) {
						addSmallText(llGoalsArchive, "No campus goals created yet.");
						return;
					}
					for (DocumentSnapshot doc : snap.getDocuments()) {
						CampusGoal goal = doc.toObject(CampusGoal.class);
						if (goal == null) continue;
						goal.setGoalId(doc.getId());
						addGoalRow(goal);
					}
				})
				.addOnFailureListener(e -> addSmallText(llGoalsArchive, "Could not load goals."));
	}

	/**
	 * AC 5 (Campus Goals story): renders one goal in the archive with title, timeline,
	 * completion status, progress bar, and a top-contributors mini list.
	 */
	private void addGoalRow(CampusGoal goal) {
		LinearLayout container = new LinearLayout(this);
		container.setOrientation(LinearLayout.VERTICAL);
		container.setPadding(0, dp(6), 0, dp(10));
		int dividerColor = Color.parseColor("#E0E0E0");
		container.setBackground(null);
		LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		clp.setMargins(0, 0, 0, dp(2));
		container.setLayoutParams(clp);

		double pct = Math.min(100, goal.getProgressPercentage());
		String statusLabel = CampusGoal.STATUS_COMPLETED.equals(goal.getStatus())
				? "✅ Achieved" : CampusGoal.STATUS_ARCHIVED.equals(goal.getStatus())
				? "📦 Archived" : "🟢 Active";

		// Title + status row
		LinearLayout titleRow = new LinearLayout(this);
		titleRow.setOrientation(LinearLayout.HORIZONTAL);
		titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

		TextView tvTitle = new TextView(this);
		tvTitle.setText(goal.getTitle() != null ? goal.getTitle() : "Campus Goal");
		tvTitle.setTextSize(14);
		tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
		tvTitle.setTextColor(Color.parseColor("#212121"));
		tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

		TextView tvStatus = new TextView(this);
		tvStatus.setText(statusLabel);
		tvStatus.setTextSize(11);
		tvStatus.setTextColor(Color.parseColor("#607D8B"));
		tvStatus.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

		titleRow.addView(tvTitle);
		titleRow.addView(tvStatus);
		container.addView(titleRow);

		// Progress text
		TextView tvProgress = new TextView(this);
		tvProgress.setText(String.format(Locale.getDefault(),
				"%.0f / %.0f %s  •  %.0f%% complete",
				goal.getCurrentProgress(), goal.getTargetValue(),
				unitForGoal(goal.getMetricType()), pct));
		tvProgress.setTextSize(12);
		tvProgress.setTextColor(Color.parseColor("#607D8B"));
		tvProgress.setPadding(0, dp(3), 0, dp(5));
		container.addView(tvProgress);

		// Progress bar (AC 3: visible shared progress tracker)
		ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
		pb.setMax(100);
		pb.setProgress((int) pct);
		pb.setProgressTintList(android.content.res.ColorStateList.valueOf(
				pct >= 100 ? Color.parseColor("#22C55E") : Color.parseColor("#009688")));
		pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(
				Color.parseColor("#E0E0E0")));
		LinearLayout.LayoutParams pblp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
		pblp.setMargins(0, 0, 0, dp(6));
		pb.setLayoutParams(pblp);
		container.addView(pb);

		// Top contributors (AC 5): fetch top 3 users by points in this period
		loadTopContributorsForGoal(goal, container);

		// Divider
		View divider = new View(this);
		divider.setBackgroundColor(dividerColor);
		divider.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
		container.addView(divider);

		llGoalsArchive.addView(container);
	}

	/**
	 * Loads the top 3 contributors for a campus goal by querying all user logs in
	 * the goal's time window and ranking them by CO₂ / waste / energy contributed.
	 */
	private void loadTopContributorsForGoal(CampusGoal goal, LinearLayout container) {
		Timestamp from = goal.getStartDate() != null ? goal.getStartDate()
				: new Timestamp(0, 0);
		Timestamp to   = goal.getEndDate()   != null ? goal.getEndDate() : Timestamp.now();
		String metric  = goal.getMetricType();

		db.collection("users").get()
				.addOnSuccessListener(usersSnap -> {
					Map<String, Double> scores = new HashMap<>();
					Map<String, String> names  = new HashMap<>();
					final int[] remaining = {usersSnap.size()};
					if (remaining[0] == 0) {
						renderTopContributors(scores, names, container);
						return;
					}
					for (var userDoc : usersSnap.getDocuments()) {
						String uid = userDoc.getId();
						String name = userDoc.getString("name");
						names.put(uid, name != null ? name : "Anonymous");
						db.collection("users").document(uid).collection("activityLogs")
								.whereGreaterThanOrEqualTo("date", from)
								.whereLessThanOrEqualTo("date", to)
								.get()
								.addOnSuccessListener(logSnap -> {
									double total = 0;
									for (var logDoc : logSnap.getDocuments()) {
										String cat = logDoc.getString("category");
										if (CampusGoal.METRIC_CO2.equals(metric)) {
											Double co2 = logDoc.getDouble("co2EquivalentKg");
											if (co2 != null) total += co2;
										} else if (CampusGoal.METRIC_WASTE.equals(metric)
												&& ActivityLog.CATEGORY_WASTE.equals(cat)) {
											Double wKg = logDoc.getDouble("wasteKg");
											if (wKg != null) total += wKg;
										} else if (CampusGoal.METRIC_ENERGY.equals(metric)
												&& ActivityLog.CATEGORY_ENERGY.equals(cat)) {
											Double eKwh = logDoc.getDouble("energyKwh");
											if (eKwh != null) total += eKwh;
										}
									}
									if (total > 0) scores.put(uid, total);
									if (--remaining[0] == 0)
										runOnUiThread(() -> renderTopContributors(scores, names, container));
								})
								.addOnFailureListener(e -> {
									if (--remaining[0] == 0)
										runOnUiThread(() -> renderTopContributors(scores, names, container));
								});
					}
				});
	}

	private void renderTopContributors(Map<String, Double> scores, Map<String, String> names,
	                                    LinearLayout container) {
		if (scores.isEmpty()) return;
		List<Map.Entry<String, Double>> sorted = new ArrayList<>(scores.entrySet());
		sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
		int limit = Math.min(3, sorted.size());

		TextView tvLabel = new TextView(this);
		tvLabel.setText("Top Contributors");
		tvLabel.setTextSize(11);
		tvLabel.setTextColor(Color.parseColor("#757575"));
		tvLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
		tvLabel.setAllCaps(true);
		tvLabel.setPadding(0, dp(4), 0, dp(4));
		container.addView(tvLabel);

		String[] medals = {"🥇 ", "🥈 ", "🥉 "};
		for (int i = 0; i < limit; i++) {
			Map.Entry<String, Double> entry = sorted.get(i);
			String name = names.getOrDefault(entry.getKey(), "Anonymous");
			TextView tv = new TextView(this);
			tv.setText(medals[i] + name + " — " + String.format(Locale.getDefault(), "%.1f", entry.getValue()));
			tv.setTextSize(12);
			tv.setTextColor(Color.parseColor("#424242"));
			tv.setPadding(0, dp(2), 0, dp(2));
			container.addView(tv);
		}
	}

	/**
	 * AC 1 (Campus Goals story): dedicated goal-creation dialog with metric type,
	 * target value, and timeline fields.
	 */
	private void showCreateGoalDialog() {
		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		int pad = dp(16);
		layout.setPadding(pad, pad, pad, 0);

		EditText etTitle = new EditText(this);
		etTitle.setHint("Goal title (e.g. Save 10,000 kg CO₂)");
		etTitle.setText("Save 10,000 kg CO₂ this semester");
		layout.addView(etTitle);

		// Metric type selector
		Spinner spinnerMetric = new Spinner(this);
		String[] metricOptions = {"CO\u2082 Saved (kg)", "Waste Diverted (kg)", "Energy Saved (kWh)"};
		String[] metricValues  = {CampusGoal.METRIC_CO2, CampusGoal.METRIC_WASTE, CampusGoal.METRIC_ENERGY};
		ArrayAdapter<String> metricAdapter = new ArrayAdapter<>(
				this, android.R.layout.simple_spinner_item, metricOptions);
		metricAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerMetric.setAdapter(metricAdapter);
		LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		spinnerLp.setMargins(0, dp(8), 0, 0);
		spinnerMetric.setLayoutParams(spinnerLp);
		layout.addView(spinnerMetric);

		EditText etTarget = new EditText(this);
		etTarget.setHint("Target value");
		etTarget.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
				| android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
		etTarget.setText("10000");
		LinearLayout.LayoutParams targetLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		targetLp.setMargins(0, dp(8), 0, 0);
		etTarget.setLayoutParams(targetLp);
		layout.addView(etTarget);

		EditText etDays = new EditText(this);
		etDays.setHint("Timeline in days");
		etDays.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
		etDays.setText("120");
		LinearLayout.LayoutParams daysLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		daysLp.setMargins(0, dp(8), 0, dp(8));
		etDays.setLayoutParams(daysLp);
		layout.addView(etDays);

		new AlertDialog.Builder(this)
				.setTitle("Create Campus Goal")
				.setView(layout)
				.setPositiveButton("Create", (dialog, which) -> {
					double targetValue;
					int durationDays;
					try {
						targetValue = Double.parseDouble(etTarget.getText().toString().trim());
						durationDays = Integer.parseInt(etDays.getText().toString().trim());
					} catch (Exception e) {
						Toast.makeText(this, "Enter valid target and timeline.", Toast.LENGTH_SHORT).show();
						return;
					}
					String goalTitle = etTitle.getText().toString().trim();
					if (goalTitle.isEmpty()) goalTitle = "Campus Goal";
					String metricType = metricValues[spinnerMetric.getSelectedItemPosition()];

					Calendar end = Calendar.getInstance();
					end.add(Calendar.DAY_OF_YEAR, durationDays);
					Map<String, Object> goal = new HashMap<>();
					goal.put("title", goalTitle);
					goal.put("metricType", metricType);
					goal.put("targetValue", targetValue);
					goal.put("currentProgress", 0.0);
					goal.put("startDate", Timestamp.now());
					goal.put("endDate", new Timestamp(end.getTimeInMillis() / 1000, 0));
					goal.put("status", CampusGoal.STATUS_ACTIVE);
					goal.put("notificationMilestones", Arrays.asList(25, 50, 75, 100));
					db.collection("campusGoals").add(goal)
							.addOnSuccessListener(ref -> {
								Toast.makeText(this, "Campus goal created.", Toast.LENGTH_SHORT).show();
								loadGoalsArchive();
							})
							.addOnFailureListener(e ->
									Toast.makeText(this, "Goal creation failed.", Toast.LENGTH_SHORT).show());
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void exportCampusCsv() {
		try {
			File file = new File(getExternalFilesDir(null), "campus_impact_report.csv");
			try (FileOutputStream out = new FileOutputStream(file)) {
				out.write(latestCsv.getBytes(StandardCharsets.UTF_8));
			}
			Toast.makeText(this, "Report saved: " + file.getName(), Toast.LENGTH_LONG).show();
		} catch (Exception e) {
			Toast.makeText(this, "Export failed.", Toast.LENGTH_SHORT).show();
		}
	}

	private void addSmallText(LinearLayout parent, String text) {
		TextView tv = new TextView(this);
		tv.setText(text);
		tv.setTextSize(12);
		tv.setTextColor(Color.parseColor("#757575"));
		tv.setPadding(0, dp(6), 0, dp(6));
		parent.addView(tv);
	}

	private String unitForGoal(String metricType) {
		if (CampusGoal.METRIC_WASTE.equals(metricType)) return "kg waste";
		if (CampusGoal.METRIC_ENERGY.equals(metricType)) return "kWh";
		return "kg CO₂";
	}

	private int dp(int value) {
		return (int) (value * getResources().getDisplayMetrics().density);
	}

}
