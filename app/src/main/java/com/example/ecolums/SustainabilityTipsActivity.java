package com.example.ecolums;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity that lists all published sustainability tips for students.
 *
 * <p>Loads {@link SustainabilityTip} documents from Firestore and displays them in
 * a RecyclerView via {@link SustainabilityTipAdapter}. Tapping a tip navigates to
 * {@link TipDetailActivity}. Supports category filtering via a chip group.</p>
 * <p>
 * Outstanding issues: Category filter chips are rendered but the filter query
 * is not fully wired to Firestore — all tips load regardless of selection.
 */
public class SustainabilityTipsActivity extends AppCompatActivity {

	private FirebaseFirestore db;
	private SustainabilityTipAdapter adapter;
	private final List<SustainabilityTip> allTips = new ArrayList<>();
	private RecyclerView rvTips;
	private LinearLayout llEmpty;
	private String activeFilter = null; // null = All

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sustainability_tips);

		db = FirebaseFirestore.getInstance();

		Toolbar toolbar = findViewById(R.id.toolbar_tips);
		setSupportActionBar(toolbar);
		if (getSupportActionBar() != null) {
			getSupportActionBar().setTitle("Sustainability Tips");
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}
		toolbar.setNavigationOnClickListener(v -> finish());

		rvTips = findViewById(R.id.rv_tips);
		llEmpty = findViewById(R.id.ll_empty);

		adapter = new SustainabilityTipAdapter(new ArrayList<>(), this::openDetail);
		rvTips.setLayoutManager(new LinearLayoutManager(this));
		rvTips.setAdapter(adapter);

		setupFilterChips();
		loadAllTips();
	}

	private void setupFilterChips() {
		Chip chipAll = findViewById(R.id.chip_all);
		Chip chipTransport = findViewById(R.id.chip_transport);
		Chip chipEnergy = findViewById(R.id.chip_energy);
		Chip chipWaste = findViewById(R.id.chip_waste);
		Chip chipGeneral = findViewById(R.id.chip_general);

		chipAll.setOnClickListener(v -> applyFilter(null));
		chipTransport.setOnClickListener(v -> applyFilter(SustainabilityTip.CATEGORY_TRANSPORT));
		chipEnergy.setOnClickListener(v -> applyFilter(SustainabilityTip.CATEGORY_ENERGY));
		chipWaste.setOnClickListener(v -> applyFilter(SustainabilityTip.CATEGORY_WASTE));
		chipGeneral.setOnClickListener(v -> applyFilter(SustainabilityTip.CATEGORY_GENERAL));
	}

	/**
	 * Load all tips once, then filter client-side to avoid needing Firestore composite indexes.
	 */
	private void loadAllTips() {
		db.collection("sustainabilityTips")
				.orderBy("publishedAt", Query.Direction.DESCENDING)
				.get()
				.addOnSuccessListener(snap -> {
					allTips.clear();
					for (var doc : snap.getDocuments()) {
						SustainabilityTip tip = doc.toObject(SustainabilityTip.class);
						if (tip != null) {
							tip.setTipId(doc.getId());
							allTips.add(tip);
						}
					}
					applyFilter(activeFilter);

					// Batch-increment view counts (fire-and-forget)
					for (var doc : snap.getDocuments()) {
						db.collection("sustainabilityTips")
								.document(doc.getId())
								.update(
										"viewCount",
										com.google.firebase.firestore.FieldValue.increment(1)
								);
					}
				})
				.addOnFailureListener(e -> showEmpty());
	}

	private void applyFilter(String category) {
		activeFilter = category;
		List<SustainabilityTip> filtered = new ArrayList<>();
		for (SustainabilityTip tip : allTips) {
			if (category == null || category.equals(tip.getCategory())) {
				filtered.add(tip);
			}
		}
		adapter.setTips(filtered);
		boolean empty = filtered.isEmpty();
		rvTips.setVisibility(empty ? View.GONE : View.VISIBLE);
		llEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
	}

	private void showEmpty() {
		rvTips.setVisibility(View.GONE);
		llEmpty.setVisibility(View.VISIBLE);
	}

	private void openDetail(SustainabilityTip tip) {
		Intent intent = new Intent(this, TipDetailActivity.class);
		intent.putExtra("tipId", tip.getTipId());
		intent.putExtra("title", tip.getTitle());
		intent.putExtra("summary", tip.getSummary());
		intent.putExtra("body", tip.getBodyContent());
		intent.putExtra("category", tip.getCategory());
		intent.putExtra("likes", tip.getLikeCount());
		intent.putExtra("views", tip.getViewCount());
		startActivity(intent);
	}
}
