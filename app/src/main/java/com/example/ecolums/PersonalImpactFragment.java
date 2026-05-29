package com.example.ecolums;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Personal Impact Dashboard — US_01.02.
 *
 * <p>Displays a student's personal sustainability progress with:
 * <ul>
 *   <li>Summary cards: total CO&#8322; saved, % change vs previous period, green points.</li>
 *   <li>A {@link LineChart} of CO&#8322; savings over time (daily / weekly / monthly).</li>
 *   <li>A {@link BarChart} of CO&#8322; breakdown by category (Transport, Energy, Waste).</li>
 * </ul>
 *
 * <p>A Firestore real-time snapshot listener automatically re-renders all
 * visualisations whenever a new activity log is submitted (AC 3).</p>
 *
 * <p>The timeframe toggle switches between Daily (last 7 days), Weekly (last
 * 4 weeks), and Monthly (last 12 months) without issuing a new Firestore
 * query — it filters the in-memory log list instead.</p>
 */
public class PersonalImpactFragment extends Fragment {

	// ── Timeframe mode constants ───────────────────────────────────────────
	private static final int MODE_DAILY   = 0;
	private static final int MODE_WEEKLY  = 1;
	private static final int MODE_MONTHLY = 2;

	// ── State ─────────────────────────────────────────────────────────────
	private int current_mode = MODE_WEEKLY;
	private final List<ActivityLog> all_logs = new ArrayList<>();

	// ── Views ─────────────────────────────────────────────────────────────
	private TextView  tv_co2_total;
	private TextView  tv_points_total;
	private TextView  tv_no_data;
	private TextView  btn_daily;
	private TextView  btn_weekly;
	private TextView  btn_monthly;
	private BarChart  bar_chart;
	private LineChart line_chart;

	// ── Firebase ──────────────────────────────────────────────────────────
	private FirebaseFirestore    db;
	private ListenerRegistration snapshot_listener;

	// ── Fragment lifecycle ────────────────────────────────────────────────

	/**
	 * Inflates the fragment layout, binds views, and attaches the real-time
	 * Firestore listener.
	 */
	@Nullable
	@Override
	public View onCreateView(
			@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState
	) {
		View view = inflater.inflate(R.layout.fragment_personal_impact, container, false);
		db = FirebaseFirestore.getInstance();

		tv_co2_total    = view.findViewById(R.id.tv_co2_total);
		tv_points_total = view.findViewById(R.id.tv_points_total);
		tv_no_data      = view.findViewById(R.id.tv_no_data);
		btn_daily       = view.findViewById(R.id.btn_daily);
		btn_weekly      = view.findViewById(R.id.btn_weekly);
		btn_monthly     = view.findViewById(R.id.btn_monthly);
		bar_chart       = view.findViewById(R.id.bar_chart);
		line_chart      = view.findViewById(R.id.line_chart);

		setup_mode_buttons();
		setup_bar_chart_style();
		setup_line_chart_style();
		attach_snapshot_listener();

		return view;
	}

	/**
	 * Removes the Firestore snapshot listener to prevent memory leaks.
	 */
	@Override
	public void onDestroyView() {
		super.onDestroyView();
		if (snapshot_listener != null) snapshot_listener.remove();
	}

	// ── Real-time listener ────────────────────────────────────────────────

	/**
	 * Attaches a Firestore real-time listener to the current user's
	 * {@code activityLogs} sub-collection. Re-renders the full dashboard on
	 * every snapshot change, satisfying AC 3 (real-time updates).
	 */
	private void attach_snapshot_listener() {
		User user = UserSession.getInstance().getCurrentUser();
		if (user == null) return;

		snapshot_listener = db.collection("users")
				.document(user.getUserId())
				.collection("activityLogs")
				.addSnapshotListener((snapshot, error) -> {
					if (error != null) {
						Log.e("PersonalImpact", "Snapshot error: " + error.getMessage());
						return;
					}
					if (!isAdded() || snapshot == null) return;
					all_logs.clear();
					for (var doc : snapshot.getDocuments()) {
						ActivityLog log = doc.toObject(ActivityLog.class);
						if (log == null) continue;
						log.setLogId(doc.getId());
						all_logs.add(log);
					}
					render_dashboard();
				});
	}

	// ── Timeframe toggle ──────────────────────────────────────────────────

	/**
	 * Wires the three timeframe toggle buttons and applies the initial
	 * visual selection state (Weekly is selected by default).
	 */
	private void setup_mode_buttons() {
		btn_daily.setOnClickListener(v -> switch_mode(MODE_DAILY));
		btn_weekly.setOnClickListener(v -> switch_mode(MODE_WEEKLY));
		btn_monthly.setOnClickListener(v -> switch_mode(MODE_MONTHLY));
		apply_mode_ui();
	}

	/**
	 * Switches the active timeframe, updates button visuals, and refreshes
	 * the dashboard by re-filtering the in-memory log list.
	 *
	 * @param mode One of {@link #MODE_DAILY}, {@link #MODE_WEEKLY},
	 *             or {@link #MODE_MONTHLY}.
	 */
	private void switch_mode(int mode) {
		current_mode = mode;
		apply_mode_ui();
		render_dashboard();
	}

	private void apply_mode_ui() {
		update_tab_style(btn_daily,   current_mode == MODE_DAILY);
		update_tab_style(btn_weekly,  current_mode == MODE_WEEKLY);
		update_tab_style(btn_monthly, current_mode == MODE_MONTHLY);
	}

	private void update_tab_style(TextView tab, boolean selected) {
		tab.setBackgroundResource(
				selected ? R.drawable.bg_tab_selected : android.R.color.transparent
		);
		tab.setTextColor(getResources().getColor(
				selected ? R.color.white : R.color.text_secondary, null
		));
	}

	// ── Dashboard rendering ───────────────────────────────────────────────

	/**
	 * Filters the cached log list into current and previous time windows,
	 * computes summary metrics, and updates all charts and text views.
	 */
	private void render_dashboard() {
		if (!isAdded()) return;

		long[] current = get_current_range();

		List<ActivityLog> cur_logs = filter_logs(all_logs, current[0], current[1]);

		boolean no_data = cur_logs.isEmpty();
		tv_no_data.setVisibility(no_data ? View.VISIBLE : View.GONE);
		line_chart.setVisibility(no_data ? View.GONE : View.VISIBLE);
		bar_chart.setVisibility(no_data ? View.GONE : View.VISIBLE);

		update_summary(sum_co2(cur_logs), sum_points(cur_logs));

		if (!no_data) {
			update_bar_chart(cur_logs);
			update_line_chart(cur_logs, current);
		}
	}

	/**
	 * Returns {@code [fromMs, toMs]} for the window ending at the current moment,
	 * sized according to the active timeframe mode.
	 */
	private long[] get_current_range() {
		long to = Calendar.getInstance().getTimeInMillis();
		Calendar start = Calendar.getInstance();

		switch (current_mode) {
		case MODE_DAILY:
			start.add(Calendar.DAY_OF_YEAR, -6);
			start.set(Calendar.HOUR_OF_DAY, 0);
			start.set(Calendar.MINUTE, 0);
			start.set(Calendar.SECOND, 0);
			start.set(Calendar.MILLISECOND, 0);
			break;
		case MODE_WEEKLY:
			start.add(Calendar.WEEK_OF_YEAR, -3);
			start.set(Calendar.DAY_OF_WEEK, start.getFirstDayOfWeek());
			start.set(Calendar.HOUR_OF_DAY, 0);
			start.set(Calendar.MINUTE, 0);
			start.set(Calendar.SECOND, 0);
			start.set(Calendar.MILLISECOND, 0);
			break;
		case MODE_MONTHLY:
		default:
			start.add(Calendar.MONTH, -11);
			start.set(Calendar.DAY_OF_MONTH, 1);
			start.set(Calendar.HOUR_OF_DAY, 0);
			start.set(Calendar.MINUTE, 0);
			start.set(Calendar.SECOND, 0);
			start.set(Calendar.MILLISECOND, 0);
			break;
		}
		return new long[]{start.getTimeInMillis(), to};
	}

	private List<ActivityLog> filter_logs(
			List<ActivityLog> logs, long from_ms, long to_ms
	) {
		List<ActivityLog> result = new ArrayList<>();
		for (ActivityLog log : logs) {
			if (log.getDate() == null) continue;
			long ts = log.getDate().getSeconds() * 1000L;
			if (ts >= from_ms && ts <= to_ms) result.add(log);
		}
		return result;
	}

	private double sum_co2(List<ActivityLog> logs) {
		double total = 0;
		for (ActivityLog log : logs) total += log.getCo2EquivalentKg();
		return total;
	}

	private double sum_points(List<ActivityLog> logs) {
		double total = 0;
		for (ActivityLog log : logs) total += log.getPointsEarned();
		return total;
	}

	// ── Summary cards ─────────────────────────────────────────────────────

	/**
	 * Updates the two metric summary cards (AC 4).
	 *
	 * @param cur_co2 Total CO&#8322; saved (kg) in the current period.
	 * @param cur_pts Green points earned in the current period.
	 */
	private void update_summary(double cur_co2, double cur_pts) {
		tv_co2_total.setText(String.format(Locale.getDefault(), "%.1f kg", cur_co2));
		tv_points_total.setText(String.format(Locale.getDefault(), "%.0f pts", cur_pts));
	}

	// ── Bar chart ─────────────────────────────────────────────────────────

	/**
	 * Configures the static visual style of the category breakdown
	 * {@link BarChart}.
	 */
	private void setup_bar_chart_style() {
		bar_chart.getDescription().setEnabled(false);
		bar_chart.getLegend().setEnabled(false);
		bar_chart.setTouchEnabled(false);
		bar_chart.getAxisRight().setEnabled(false);
		bar_chart.getAxisLeft().setAxisMinimum(0f);
		bar_chart.getAxisLeft().setTextColor(Color.parseColor("#999999"));
		bar_chart.getAxisLeft().setGridColor(Color.parseColor("#22000000"));
		bar_chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
		bar_chart.getXAxis().setDrawGridLines(false);
		bar_chart.getXAxis().setTextColor(Color.parseColor("#999999"));
		bar_chart.getXAxis().setGranularity(1f);
		bar_chart.setFitBars(true);
		bar_chart.setExtraBottomOffset(8f);
	}

	/**
	 * Renders a three-bar breakdown of CO&#8322; by activity category for the
	 * current period (AC 2 — bar chart visualisation).
	 *
	 * @param logs Activity logs for the current period.
	 */
	private void update_bar_chart(List<ActivityLog> logs) {
		double transport = 0, energy = 0, waste = 0;
		for (ActivityLog log : logs) {
			switch (log.getCategory()) {
			case ActivityLog.CATEGORY_TRANSPORT:
				transport += log.getCo2EquivalentKg();
				break;
			case ActivityLog.CATEGORY_ENERGY:
				energy += log.getCo2EquivalentKg();
				break;
			case ActivityLog.CATEGORY_WASTE:
				waste += log.getCo2EquivalentKg();
				break;
			}
		}

		List<BarEntry> entries = new ArrayList<>();
		entries.add(new BarEntry(0f, (float) Math.max(0, transport)));
		entries.add(new BarEntry(1f, (float) Math.max(0, energy)));
		entries.add(new BarEntry(2f, (float) Math.max(0, waste)));

		BarDataSet dataset = new BarDataSet(entries, "");
		dataset.setColors(
				Color.parseColor("#F97316"),  // orange — Transport
				Color.parseColor("#3B82F6"),  // blue   — Energy
				Color.parseColor("#A78BFA")   // purple — Waste
		);
		dataset.setDrawValues(true);
		dataset.setValueTextSize(10f);
		dataset.setValueTextColor(Color.parseColor("#444444"));

		BarData data = new BarData(dataset);
		data.setBarWidth(0.5f);
		bar_chart.setData(data);
		bar_chart.getXAxis().setValueFormatter(
				new IndexAxisValueFormatter(new String[]{"Transport", "Energy", "Waste"})
		);
		bar_chart.getXAxis().setLabelCount(3);
		bar_chart.animateY(500);
		bar_chart.invalidate();
	}

	// ── Line chart ────────────────────────────────────────────────────────

	/**
	 * Configures the static visual style of the CO&#8322;-over-time
	 * {@link LineChart}.
	 */
	private void setup_line_chart_style() {
		line_chart.getDescription().setEnabled(false);
		line_chart.getLegend().setEnabled(false);
		line_chart.setTouchEnabled(true);
		line_chart.getAxisRight().setEnabled(false);
		line_chart.getAxisLeft().setAxisMinimum(0f);
		line_chart.getAxisLeft().setTextColor(Color.parseColor("#999999"));
		line_chart.getAxisLeft().setGridColor(Color.parseColor("#22000000"));
		line_chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
		line_chart.getXAxis().setDrawGridLines(false);
		line_chart.getXAxis().setTextColor(Color.parseColor("#999999"));
		line_chart.setExtraBottomOffset(12f);
	}

	/**
	 * Renders a smooth filled teal line of total CO&#8322; savings over the current
	 * time period (AC 2 — line chart visualisation).
	 *
	 * @param logs  Activity logs for the current period.
	 * @param range {@code [fromMs, toMs]} of the current period.
	 */
	private void update_line_chart(List<ActivityLog> logs, long[] range) {
		List<String> x_labels = build_x_labels(range);
		Map<String, Double> series = bucket_by_label(logs);

		List<Entry> entries = new ArrayList<>();
		for (int i = 0; i < x_labels.size(); i++) {
			String label = x_labels.get(i);
			float val = series.containsKey(label) ? series.get(label).floatValue() : 0f;
			entries.add(new Entry(i, Math.max(0f, val)));
		}

		LineDataSet dataset = new LineDataSet(entries, "");
		dataset.setColor(Color.parseColor("#00BCD4"));
		dataset.setFillColor(Color.parseColor("#3300BCD4"));
		dataset.setFillAlpha(60);
		dataset.setDrawFilled(true);
		dataset.setLineWidth(2.5f);
		dataset.setDrawCircles(false);
		dataset.setDrawValues(false);
		dataset.setMode(LineDataSet.Mode.CUBIC_BEZIER);

		line_chart.setData(new LineData(dataset));
		line_chart.getXAxis().setValueFormatter(
				new IndexAxisValueFormatter(x_labels.toArray(new String[0]))
		);
		int label_count = Math.min(x_labels.size(), current_mode == MODE_DAILY ? 7 : 6);
		line_chart.getXAxis().setLabelCount(label_count, true);
		line_chart.getXAxis().setLabelRotationAngle(-30f);
		line_chart.animateX(600);
		line_chart.invalidate();
	}

	/**
	 * Builds the ordered list of X-axis bucket labels for the given range
	 * and active mode.
	 *
	 * @param range {@code [fromMs, toMs]}.
	 * @return Labels in chronological order (oldest first).
	 */
	private List<String> build_x_labels(long[] range) {
		List<String> labels = new ArrayList<>();
		Calendar cursor = Calendar.getInstance();
		cursor.setTimeInMillis(range[0]);

		switch (current_mode) {
		case MODE_DAILY: {
			SimpleDateFormat sdf = new SimpleDateFormat("d MMM", Locale.getDefault());
			for (int i = 0; i < 7; i++) {
				labels.add(sdf.format(cursor.getTime()));
				cursor.add(Calendar.DAY_OF_YEAR, 1);
			}
			break;
		}
		case MODE_WEEKLY: {
			SimpleDateFormat sdf = new SimpleDateFormat("d MMM", Locale.getDefault());
			cursor.set(Calendar.DAY_OF_WEEK, cursor.getFirstDayOfWeek());
			for (int i = 0; i < 4; i++) {
				labels.add(sdf.format(cursor.getTime()));
				cursor.add(Calendar.WEEK_OF_YEAR, 1);
			}
			break;
		}
		case MODE_MONTHLY:
		default: {
			SimpleDateFormat sdf = new SimpleDateFormat("MMM", Locale.getDefault());
			for (int i = 0; i < 12; i++) {
				labels.add(sdf.format(cursor.getTime()));
				cursor.add(Calendar.MONTH, 1);
			}
			break;
		}
		}
		return labels;
	}

	/**
	 * Groups each log's CO&#8322; value into its time bucket label under the
	 * current mode.
	 *
	 * @param logs Logs to bucket.
	 * @return Map of bucket label to total CO&#8322; (kg).
	 */
	private Map<String, Double> bucket_by_label(List<ActivityLog> logs) {
		Map<String, Double> map = new LinkedHashMap<>();
		for (ActivityLog log : logs) {
			if (log.getDate() == null) continue;
			String label = get_bucket_label(log.getDate().getSeconds() * 1000L);
			map.merge(label, log.getCo2EquivalentKg(), Double::sum);
		}
		return map;
	}

	/**
	 * Returns the X-axis bucket label for a log's timestamp under the
	 * current timeframe mode.
	 *
	 * @param timestamp_ms Unix timestamp in milliseconds.
	 * @return Human-readable bucket label matching one entry in
	 *         {@link #build_x_labels}.
	 */
	private String get_bucket_label(long timestamp_ms) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(timestamp_ms);
		switch (current_mode) {
		case MODE_DAILY:
			return new SimpleDateFormat("d MMM", Locale.getDefault()).format(cal.getTime());
		case MODE_WEEKLY:
			cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
			return new SimpleDateFormat("d MMM", Locale.getDefault()).format(cal.getTime());
		case MODE_MONTHLY:
		default:
			return new SimpleDateFormat("MMM", Locale.getDefault()).format(cal.getTime());
		}
	}
}
