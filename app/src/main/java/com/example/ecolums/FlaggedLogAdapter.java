package com.example.ecolums;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RecyclerView adapter for the flagged-log detail view in
 * {@link FlaggedLogsBottomSheet}.
 *
 * <p>Each row shows: category + emoji, activity detail (mode/type + quantity),
 * anomaly score with severity colouring, CO₂ estimate, points earned,
 * and the log timestamp.</p>
 */
public class FlaggedLogAdapter extends RecyclerView.Adapter<FlaggedLogAdapter.VH> {

    private final List<Map<String, Object>> items;

    public FlaggedLogAdapter(List<Map<String, Object>> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_flagged_log, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Map<String, Object> d = items.get(pos);

        String category = getString(d, "category");
        h.tvCategory.setText(categoryEmoji(category) + "  " + category);

        h.tvDetail.setText(buildDetail(d, category));

        // Anomaly score
        double score = toDouble(d.get("anomalyScore"));
        h.tvScore.setText(String.format(Locale.getDefault(), "Anomaly score: %.4f", score));
        h.tvScore.setTextColor(score > 0.3
                ? Color.parseColor("#C62828")   // dark red — very suspicious
                : Color.parseColor("#E65100")); // deep orange — suspicious

        // CO₂ and points
        double co2 = toDouble(d.get("co2EquivalentKg"));
        double pts = toDouble(d.get("pointsEarned"));
        h.tvCo2.setText(String.format(Locale.getDefault(),
                "CO₂: %.3f kg  •  Pts: %.0f", co2, pts));

        // Timestamp
        Object dateObj = d.get("date");
        if (dateObj instanceof Timestamp) {
            h.tvDate.setText(new SimpleDateFormat("MMM d, yyyy  hh:mm a", Locale.getDefault())
                    .format(((Timestamp) dateObj).toDate()));
        } else {
            h.tvDate.setText("—");
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildDetail(Map<String, Object> d, String category) {
        switch (category != null ? category : "") {
            case ActivityLog.CATEGORY_TRANSPORT: {
                String mode = getString(d, "transportMode");
                double km   = toDouble(d.get("distanceKm"));
                return mode + "  —  "
                        + String.format(Locale.getDefault(), "%.1f km", km);
            }
            case ActivityLog.CATEGORY_ENERGY: {
                double kwh = toDouble(d.get("energyKwh"));
                return String.format(Locale.getDefault(), "%.2f kWh", kwh);
            }
            case ActivityLog.CATEGORY_WASTE: {
                String type = getString(d, "wasteType");
                double kg   = toDouble(d.get("wasteKg"));
                return type + "  —  "
                        + String.format(Locale.getDefault(), "%.2f kg", kg);
            }
            default:
                return "";
        }
    }

    private String categoryEmoji(String cat) {
        if (ActivityLog.CATEGORY_TRANSPORT.equals(cat)) return "🚌";
        if (ActivityLog.CATEGORY_ENERGY.equals(cat))    return "⚡";
        if (ActivityLog.CATEGORY_WASTE.equals(cat))     return "♻";
        return "❓";
    }

    private double toDouble(Object obj) {
        return obj instanceof Number ? ((Number) obj).doubleValue() : 0.0;
    }

    private String getString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : "";
    }

    // -------------------------------------------------------------------------
    // ViewHolder
    // -------------------------------------------------------------------------

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCategory, tvDetail, tvScore, tvCo2, tvDate;

        VH(View v) {
            super(v);
            tvCategory = v.findViewById(R.id.tv_log_category);
            tvDetail   = v.findViewById(R.id.tv_log_detail);
            tvScore    = v.findViewById(R.id.tv_log_score);
            tvCo2      = v.findViewById(R.id.tv_log_co2);
            tvDate     = v.findViewById(R.id.tv_log_date);
        }
    }
}
