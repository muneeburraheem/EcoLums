package com.example.ecolums;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for {@link AdminAnomalyFragment}.
 *
 * <p>Displays one row per user who has at least one flagged activity log,
 * showing their display name, UID, number of flagged logs, and maximum
 * anomaly score. Tapping a row invokes {@link OnUserClickListener}.</p>
 */
public class AnomalyUserAdapter extends RecyclerView.Adapter<AnomalyUserAdapter.VH> {

    // -------------------------------------------------------------------------
    // Data model
    // -------------------------------------------------------------------------

    public static class UserSummary {
        public final String userId;
        public final String displayName;
        public final int    flaggedCount;
        public final double maxScore;

        public UserSummary(String userId, String displayName, int flaggedCount, double maxScore) {
            this.userId       = userId;
            this.displayName  = displayName;
            this.flaggedCount = flaggedCount;
            this.maxScore     = maxScore;
        }
    }

    // -------------------------------------------------------------------------
    // Callback
    // -------------------------------------------------------------------------

    public interface OnUserClickListener {
        void onUserClicked(UserSummary user);
    }

    // -------------------------------------------------------------------------
    // Adapter
    // -------------------------------------------------------------------------

    private final List<UserSummary>    items;
    private final OnUserClickListener  listener;

    public AnomalyUserAdapter(List<UserSummary> items, OnUserClickListener listener) {
        this.items    = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_anomaly_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        UserSummary s = items.get(pos);

        h.tvName.setText(s.displayName);
        h.tvUserId.setText(s.userId);
        h.tvCount.setText(s.flaggedCount + (s.flaggedCount == 1 ? " flagged log" : " flagged logs"));
        h.tvScore.setText(String.format(Locale.getDefault(), "Max score: %.4f", s.maxScore));

        // Higher score → deeper red
        h.tvScore.setTextColor(s.maxScore > 0.3
                ? Color.parseColor("#C62828")   // dark red
                : Color.parseColor("#E65100")); // deep orange

        h.itemView.setOnClickListener(v -> listener.onUserClicked(s));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // -------------------------------------------------------------------------
    // ViewHolder
    // -------------------------------------------------------------------------

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvUserId, tvCount, tvScore;

        VH(View v) {
            super(v);
            tvName   = v.findViewById(R.id.tv_user_name);
            tvUserId = v.findViewById(R.id.tv_user_id);
            tvCount  = v.findViewById(R.id.tv_flagged_count);
            tvScore  = v.findViewById(R.id.tv_max_score);
        }
    }
}
