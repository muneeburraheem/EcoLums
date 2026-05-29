package com.example.ecolums;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Merch store screen where users spend green points on real-world items.
 * Atomic WriteBatch ensures stock decrement and point deduction are consistent.
 */
public class MerchStoreActivity extends AppCompatActivity {

    private RecyclerView rvMerch;
    private TextView tvUserPoints, tvEmpty, tvRedeemedEmpty;
    private LinearLayout llRedeemed;
    private FirebaseFirestore db;
    private List<MerchItem> items = new ArrayList<>();
    private MerchItemAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_merch_store);

        db = FirebaseFirestore.getInstance();
        findViewById(R.id.tv_back).setOnClickListener(v -> finish());

        rvMerch          = findViewById(R.id.rv_merch);
        tvUserPoints     = findViewById(R.id.tv_user_points);
        tvEmpty          = findViewById(R.id.tv_merch_empty);
        tvRedeemedEmpty  = findViewById(R.id.tv_redeemed_empty);
        llRedeemed       = findViewById(R.id.ll_redeemed);

        rvMerch.setLayoutManager(new GridLayoutManager(this, 2));
        rvMerch.setNestedScrollingEnabled(false);

        User user = UserSession.getInstance().getCurrentUser();
        if (user != null) {
            tvUserPoints.setText(String.format(Locale.getDefault(), "%.0f pts", user.getTotalPoints()));
        }

        loadMerch();
        loadRedemptions();
    }

    private void loadMerch() {
        User user = UserSession.getInstance().getCurrentUser();
        double userPoints = user != null ? user.getTotalPoints() : 0;

        db.collection("merch")
                .orderBy("pointsCost")
                .get()
                .addOnSuccessListener(snap -> {
                    items.clear();
                    for (var doc : snap.getDocuments()) {
                        MerchItem item = doc.toObject(MerchItem.class);
                        if (item != null) {
                            item.setItemId(doc.getId());
                            items.add(item);
                        }
                    }

                    if (items.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        rvMerch.setVisibility(View.GONE);
                    } else {
                        tvEmpty.setVisibility(View.GONE);
                        rvMerch.setVisibility(View.VISIBLE);
                        adapter = new MerchItemAdapter(items, userPoints, this::showRedeemDialog);
                        rvMerch.setAdapter(adapter);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load merch.", Toast.LENGTH_SHORT).show());
    }

    private void loadRedemptions() {
        User user = UserSession.getInstance().getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUserId())
                .collection("redemptions")
                .orderBy("redeemedAt")
                .get()
                .addOnSuccessListener(snap -> {
                    // Aggregate quantities per item name, preserving insertion order
                    Map<String, Integer> qtys = new LinkedHashMap<>();
                    Map<String, Integer> costs = new LinkedHashMap<>();
                    for (var doc : snap.getDocuments()) {
                        String name = doc.getString("itemName");
                        Long cost = doc.getLong("pointsCost");
                        if (name == null) continue;
                        qtys.put(name, qtys.getOrDefault(name, 0) + 1);
                        if (!costs.containsKey(name) && cost != null) {
                            costs.put(name, cost.intValue());
                        }
                    }

                    llRedeemed.removeAllViews();

                    if (qtys.isEmpty()) {
                        tvRedeemedEmpty.setVisibility(View.VISIBLE);
                        return;
                    }

                    tvRedeemedEmpty.setVisibility(View.GONE);
                    boolean first = true;
                    for (Map.Entry<String, Integer> entry : qtys.entrySet()) {
                        if (!first) {
                            // divider
                            View divider = new View(this);
                            divider.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
                            divider.setBackgroundColor(0xFFEEEEEE);
                            llRedeemed.addView(divider);
                        }
                        first = false;

                        View row = LayoutInflater.from(this)
                                .inflate(R.layout.item_redeemed, llRedeemed, false);

                        TextView tvName = row.findViewById(R.id.tv_redeemed_name);
                        TextView tvQty  = row.findViewById(R.id.tv_redeemed_qty);

                        tvName.setText(entry.getKey());
                        tvQty.setText("x" + entry.getValue());

                        llRedeemed.addView(row);
                    }
                });
    }

    private void showRedeemDialog(MerchItem item) {
        User user = UserSession.getInstance().getCurrentUser();
        if (user == null) return;

        double remaining = user.getTotalPoints() - item.getPointsCost();

        View v = LayoutInflater.from(this).inflate(R.layout.dialog_redeem_confirm, null);
        ((TextView) v.findViewById(R.id.tv_confirm_item_name)).setText(item.getName());
        ((TextView) v.findViewById(R.id.tv_confirm_cost))
                .setText(item.getPointsCost() + " pts");
        ((TextView) v.findViewById(R.id.tv_confirm_remaining))
                .setText(String.format(Locale.getDefault(), "%.0f pts after redemption", remaining));

        new AlertDialog.Builder(this)
                .setView(v)
                .setPositiveButton("Confirm Redeem", (d, w) -> redeemItem(item, user))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void redeemItem(MerchItem item, User user) {
        if (user.getTotalPoints() < item.getPointsCost()) {
            Toast.makeText(this, "Not enough points to redeem this item.", Toast.LENGTH_SHORT).show();
            return;
        }

        WriteBatch batch = db.batch();

        // Decrement stock
        batch.update(db.collection("merch").document(item.getItemId()),
                "availableStock", FieldValue.increment(-1));

        // Deduct points
        double newPoints = user.getTotalPoints() - item.getPointsCost();
        batch.update(db.collection("users").document(user.getUserId()),
                "totalPoints", newPoints);

        // Record redemption
        Map<String, Object> redemption = new HashMap<>();
        redemption.put("itemId", item.getItemId());
        redemption.put("itemName", item.getName());
        redemption.put("pointsCost", item.getPointsCost());
        redemption.put("redeemedAt", Timestamp.now());
        batch.set(db.collection("users").document(user.getUserId())
                .collection("redemptions").document(), redemption);

        batch.commit()
                .addOnSuccessListener(unused -> {
                    user.setTotalPoints(newPoints);
                    tvUserPoints.setText(String.format(Locale.getDefault(), "%.0f pts", newPoints));
                    item.setAvailableStock(item.getAvailableStock() - 1);
                    if (adapter != null) {
                        adapter.setUserPoints(newPoints);
                        adapter.notifyDataSetChanged();
                    }
                    Toast.makeText(this,
                            "Redeemed! Collect your " + item.getName() + " from the campus office.",
                            Toast.LENGTH_LONG).show();
                    loadRedemptions(); // refresh the redeemed section
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Redemption failed. Try again.", Toast.LENGTH_SHORT).show());
    }
}
