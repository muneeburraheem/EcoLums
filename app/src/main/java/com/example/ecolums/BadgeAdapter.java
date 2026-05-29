package com.example.ecolums;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** RecyclerView adapter for the Trophy Case badge grid. */
public class BadgeAdapter extends RecyclerView.Adapter<BadgeAdapter.VH> {

    public interface OnBadgeClickListener {
        void onBadgeClick(Badge badge);
    }

    private final List<Badge> badges;
    private final OnBadgeClickListener listener;

    public BadgeAdapter(List<Badge> badges, OnBadgeClickListener listener) {
        this.badges = badges;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_badge, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Badge badge = badges.get(pos);
        boolean earned = badge.isEarned();

        // Emoji
        h.tvEmoji.setText(badge.getDisplayEmoji());

        // Apply greyscale filter if not earned (TextView has no setColorFilter; use layer paint)
        if (earned) {
            h.tvEmoji.setLayerType(View.LAYER_TYPE_NONE, null);
            h.tvEmoji.setAlpha(1.0f);
        } else {
            ColorMatrix matrix = new ColorMatrix();
            matrix.setSaturation(0);
            Paint paint = new Paint();
            paint.setColorFilter(new ColorMatrixColorFilter(matrix));
            h.tvEmoji.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
            h.tvEmoji.setAlpha(0.5f);
        }

        h.tvName.setText(badge.getName());

        // Tier label
        if (badge.isPremium()) {
            h.tvTier.setText("✨ PREMIUM");
            h.tvTier.setTextColor(0xFFFF8F00); // amber
            h.tvTier.setVisibility(View.VISIBLE);
        } else if (Badge.TIER_BASIC.equals(badge.getBadgeTier())) {
            h.tvTier.setText("STARTER");
            h.tvTier.setTextColor(0xFF757575);
            h.tvTier.setVisibility(View.VISIBLE);
        } else {
            h.tvTier.setVisibility(View.GONE);
        }

        // Date earned
        if (earned && badge.getDateEarned() != null) {
            Date d = badge.getDateEarned().toDate();
            String dateStr = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(d);
            h.tvDate.setText(dateStr);
            h.tvDate.setVisibility(View.VISIBLE);
        } else if (!earned) {
            h.tvDate.setText("Not earned yet");
            h.tvDate.setTextColor(0xFFBDBDBD);
            h.tvDate.setVisibility(View.VISIBLE);
        } else {
            h.tvDate.setVisibility(View.GONE);
        }

        // Card border for premium earned
        if (earned && badge.isPremium()) {
            h.card.setCardBackgroundColor(0xFFFFF8E1); // warm gold tint
        } else if (earned) {
            h.card.setCardBackgroundColor(0xFFFFFFFF);
        } else {
            h.card.setCardBackgroundColor(0xFFF5F5F5);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onBadgeClick(badge);
        });
    }

    @Override
    public int getItemCount() { return badges.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvEmoji, tvName, tvTier, tvDate;
        CardView card;

        VH(View v) {
            super(v);
            card = v.findViewById(R.id.card_badge);
            tvEmoji = v.findViewById(R.id.tv_badge_emoji);
            tvName  = v.findViewById(R.id.tv_badge_name);
            tvTier  = v.findViewById(R.id.tv_badge_tier);
            tvDate  = v.findViewById(R.id.tv_badge_date);
        }
    }
}
