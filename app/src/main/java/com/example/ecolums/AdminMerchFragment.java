package com.example.ecolums;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Admin tab for managing the merch store inventory. */
public class AdminMerchFragment extends Fragment {

    private LinearLayout llMerchList;
    private FirebaseFirestore db;
    private final List<MerchItem> items = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_merch, container, false);
        db = FirebaseFirestore.getInstance();

        llMerchList = view.findViewById(R.id.ll_merch_list);
        view.findViewById(R.id.btn_add_merch).setOnClickListener(v -> showAddItemDialog());

        loadMerch();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadMerch();
    }

    private void loadMerch() {
        if (llMerchList == null) return;
        db.collection("merch").orderBy("pointsCost").get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    items.clear();
                    llMerchList.removeAllViews();
                    for (var doc : snap.getDocuments()) {
                        MerchItem item = doc.toObject(MerchItem.class);
                        if (item == null) continue;
                        item.setItemId(doc.getId());
                        items.add(item);
                        llMerchList.addView(buildItemRow(item));
                    }
                    if (items.isEmpty()) {
                        TextView empty = new TextView(getContext());
                        empty.setText("No merch items yet. Add one!");
                        empty.setTextColor(Color.parseColor("#757575"));
                        empty.setPadding(0, 16, 0, 0);
                        llMerchList.addView(empty);
                    }
                });
    }

    private View buildItemRow(MerchItem item) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 14, 0, 14);

        TextView tvEmoji = new TextView(getContext());
        tvEmoji.setText(item.getImageEmoji() != null ? item.getImageEmoji() : "🛍️");
        tvEmoji.setTextSize(26);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44));
        ep.setMarginEnd(dpToPx(12));
        tvEmoji.setLayoutParams(ep);
        tvEmoji.setGravity(Gravity.CENTER);

        LinearLayout info = new LinearLayout(getContext());
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(getContext());
        tvName.setText(item.getName());
        tvName.setTextSize(14);
        tvName.setTextColor(Color.parseColor("#212121"));
        tvName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextView tvMeta = new TextView(getContext());
        tvMeta.setText(String.format(Locale.getDefault(),
                "%d pts · Stock: %d/%d",
                item.getPointsCost(), item.getAvailableStock(), item.getTotalStock()));
        tvMeta.setTextSize(11);
        tvMeta.setTextColor(item.isAvailable()
                ? Color.parseColor("#009688") : Color.parseColor("#F44336"));

        info.addView(tvName);
        info.addView(tvMeta);

        com.google.android.material.button.MaterialButton btnRestock =
                new com.google.android.material.button.MaterialButton(requireContext());
        btnRestock.setText("Restock");
        btnRestock.setTextSize(11);
        btnRestock.setTextColor(Color.WHITE);
        btnRestock.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#009688")));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                dpToPx(80), dpToPx(36));
        bp.setMarginStart(dpToPx(8));
        btnRestock.setLayoutParams(bp);
        btnRestock.setPadding(dpToPx(4), 0, dpToPx(4), 0);
        btnRestock.setOnClickListener(v -> showRestockDialog(item));

        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        row.addView(tvEmoji);
        row.addView(info);
        row.addView(btnRestock);

        View divider = new View(getContext());
        divider.setBackgroundColor(Color.parseColor("#F0F0F0"));
        wrapper.addView(row);
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return wrapper;
    }

    private void showAddItemDialog() {
        View v = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_merch, null);
        EditText etName  = v.findViewById(R.id.et_merch_name);
        EditText etDesc  = v.findViewById(R.id.et_merch_desc);
        EditText etEmoji = v.findViewById(R.id.et_merch_emoji);
        EditText etCost  = v.findViewById(R.id.et_merch_cost);
        EditText etStock = v.findViewById(R.id.et_merch_stock);

        new AlertDialog.Builder(requireContext())
                .setTitle("Add Merch Item")
                .setView(v)
                .setPositiveButton("Add", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) { Toast.makeText(getContext(), "Name required.", Toast.LENGTH_SHORT).show(); return; }
                    String desc  = etDesc.getText().toString().trim();
                    String emoji = etEmoji.getText().toString().trim();
                    if (emoji.isEmpty()) emoji = "👕";
                    int cost  = parseSafe(etCost.getText().toString());
                    int stock = parseSafe(etStock.getText().toString());

                    User admin = UserSession.getInstance().getCurrentUser();
                    Map<String, Object> data = new HashMap<>();
                    data.put("name", name);
                    data.put("description", desc);
                    data.put("imageEmoji", emoji);
                    data.put("pointsCost", cost);
                    data.put("totalStock", stock);
                    data.put("availableStock", stock);
                    data.put("category", "Clothing");
                    data.put("adminId", admin != null ? admin.getUserId() : "");
                    data.put("createdAt", Timestamp.now());

                    db.collection("merch").add(data)
                            .addOnSuccessListener(ref -> {
                                Toast.makeText(getContext(), "Item added!", Toast.LENGTH_SHORT).show();
                                loadMerch();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(getContext(), "Failed to add item.", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRestockDialog(MerchItem item) {
        View v = LayoutInflater.from(getContext()).inflate(R.layout.dialog_restock, null);
        EditText etQty = v.findViewById(R.id.et_restock_qty);

        new AlertDialog.Builder(requireContext())
                .setTitle("Restock " + item.getName())
                .setView(v)
                .setPositiveButton("Restock", (d, w) -> {
                    int qty = parseSafe(etQty.getText().toString());
                    if (qty <= 0) return;
                    int newStock = item.getAvailableStock() + qty;
                    int newTotal = item.getTotalStock() + qty;
                    db.collection("merch").document(item.getItemId())
                            .update("availableStock", newStock, "totalStock", newTotal)
                            .addOnSuccessListener(unused -> {
                                item.setAvailableStock(newStock);
                                item.setTotalStock(newTotal);
                                Toast.makeText(getContext(), "Restocked!", Toast.LENGTH_SHORT).show();
                                loadMerch();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int parseSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
