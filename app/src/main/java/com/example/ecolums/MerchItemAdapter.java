package com.example.ecolums;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

/** RecyclerView adapter for the merch store grid. */
public class MerchItemAdapter extends RecyclerView.Adapter<MerchItemAdapter.VH> {

    public interface OnRedeemClickListener {
        void onRedeemClick(MerchItem item);
    }

    private final List<MerchItem> items;
    private double userPoints;
    private final OnRedeemClickListener listener;

    public MerchItemAdapter(List<MerchItem> items, double userPoints,
                            OnRedeemClickListener listener) {
        this.items = items;
        this.userPoints = userPoints;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_merch, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        MerchItem item = items.get(pos);

        h.tvEmoji.setText(item.getImageEmoji() != null ? item.getImageEmoji() : "🛍️");
        h.tvName.setText(item.getName());
        h.tvDesc.setText(item.getDescription() != null ? item.getDescription() : "");
        h.tvCost.setText(item.getPointsCost() + " pts");
        h.tvStock.setText(item.getAvailableStock() + " left");

        boolean canRedeem = item.isAvailable() && userPoints >= item.getPointsCost();

        h.btnRedeem.setEnabled(canRedeem);
        if (!item.isAvailable()) {
            h.btnRedeem.setText("Out of Stock");
            h.btnRedeem.setAlpha(0.5f);
        } else if (userPoints < item.getPointsCost()) {
            h.btnRedeem.setText("Need " + item.getPointsCost() + " pts");
            h.btnRedeem.setAlpha(0.6f);
        } else {
            h.btnRedeem.setText("Redeem");
            h.btnRedeem.setAlpha(1.0f);
        }

        h.btnRedeem.setOnClickListener(v -> {
            if (canRedeem && listener != null) listener.onRedeemClick(item);
        });
    }

    public void setUserPoints(double points) {
        this.userPoints = points;
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvEmoji, tvName, tvDesc, tvCost, tvStock;
        Button btnRedeem;

        VH(View v) {
            super(v);
            tvEmoji  = v.findViewById(R.id.tv_merch_emoji);
            tvName   = v.findViewById(R.id.tv_merch_name);
            tvDesc   = v.findViewById(R.id.tv_merch_desc);
            tvCost   = v.findViewById(R.id.tv_merch_cost);
            tvStock  = v.findViewById(R.id.tv_merch_stock);
            btnRedeem = v.findViewById(R.id.btn_redeem);
        }
    }
}
