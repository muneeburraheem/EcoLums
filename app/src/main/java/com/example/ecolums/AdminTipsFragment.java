package com.example.ecolums;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin tab fragment for managing sustainability tips.
 *
 * <p>Displays the list of all published {@link SustainabilityTip} objects and
 * allows admins to add new tips via a dialog form, or delete existing ones.
 * Uses {@link AdminTipAdapter} for the RecyclerView and delegates persistence
 * to {@link AdminDashboard}.</p>
 * <p>
 * Outstanding issues: Edit-in-place for existing tips is not yet implemented.
 */
public class AdminTipsFragment extends Fragment {

	private FirebaseFirestore db;
	private AdminTipAdapter adapter;
	private List<SustainabilityTip> tips = new ArrayList<>();
	private LinearLayout llEmpty;
	private RecyclerView rvTips;

	@Nullable
	@Override
	public View onCreateView(
			@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState
	) {
		View view = inflater.inflate(R.layout.fragment_admin_tips, container, false);

		db = FirebaseFirestore.getInstance();
		rvTips = view.findViewById(R.id.rv_admin_tips);
		llEmpty = view.findViewById(R.id.ll_admin_tips_empty);

		adapter = new AdminTipAdapter(
				tips, new AdminTipAdapter.OnAdminTipListener() {
			@Override
			public void onEdit(SustainabilityTip tip) {
				showAddEditDialog(tip);
			}

			@Override
			public void onDelete(SustainabilityTip tip) {
				confirmDelete(tip);
			}
		}
		);

		rvTips.setLayoutManager(new LinearLayoutManager(getContext()));
		rvTips.setAdapter(adapter);

		MaterialButton btnAdd = view.findViewById(R.id.btn_add_tip);
		btnAdd.setOnClickListener(v -> showAddEditDialog(null));

		loadTips();
		return view;
	}

	private void loadTips() {
		db.collection("sustainabilityTips")
				.orderBy("publishedAt", Query.Direction.DESCENDING)
				.get()
				.addOnSuccessListener(snap -> {
					tips.clear();
					for (var doc : snap.getDocuments()) {
						SustainabilityTip tip = doc.toObject(SustainabilityTip.class);
						if (tip != null) {
							tip.setTipId(doc.getId());
							tips.add(tip);
						}
					}
					adapter.setTips(tips);
					boolean empty = tips.isEmpty();
					rvTips.setVisibility(empty ? View.GONE : View.VISIBLE);
					llEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
				});
	}

	private void showAddEditDialog(@Nullable SustainabilityTip existing) {
		// Build dialog layout programmatically
		LinearLayout layout = new LinearLayout(getContext());
		layout.setOrientation(LinearLayout.VERTICAL);
		int pad = dpToPx(16);
		layout.setPadding(pad, pad, pad, pad);

		EditText etTitle = new EditText(getContext());
		etTitle.setHint("Title *");
		layout.addView(etTitle);

		EditText etSummary = new EditText(getContext());
		etSummary.setHint("Short summary *");
		etSummary.setMinLines(2);
		layout.addView(etSummary);

		EditText etBody = new EditText(getContext());
		etBody.setHint("Full body content *");
		etBody.setMinLines(4);
		layout.addView(etBody);

		Spinner spCategory = new Spinner(getContext());
		String[] categories = {
				SustainabilityTip.CATEGORY_GENERAL,
				SustainabilityTip.CATEGORY_TRANSPORT,
				SustainabilityTip.CATEGORY_ENERGY,
				SustainabilityTip.CATEGORY_WASTE
		};
		ArrayAdapter<String> catAdapter = new ArrayAdapter<>(
				getContext(), android.R.layout.simple_spinner_item, categories);
		catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spCategory.setAdapter(catAdapter);
		layout.addView(spCategory);

		// Pre-fill for edit
		if (existing != null) {
			etTitle.setText(existing.getTitle());
			etSummary.setText(existing.getSummary());
			etBody.setText(existing.getBodyContent());
			for (int i = 0; i < categories.length; i++) {
				if (categories[i].equals(existing.getCategory())) {
					spCategory.setSelection(i);
					break;
				}
			}
		}

		String dialogTitle = existing == null ? "Add Tip" : "Edit Tip";
		String dialogBtn = existing == null ? "Publish" : "Save";

		new AlertDialog.Builder(getContext())
				.setTitle(dialogTitle)
				.setView(layout)
				.setPositiveButton(
						dialogBtn, (d, w) -> {
							String title = etTitle.getText().toString().trim();
							String summary = etSummary.getText().toString().trim();
							String body = etBody.getText().toString().trim();
							String category = spCategory.getSelectedItem().toString();

							if (TextUtils.isEmpty(title) || TextUtils.isEmpty(summary) || TextUtils.isEmpty(
									body)) {
								Toast.makeText(
										getContext(),
										"All fields are required.",
										Toast.LENGTH_SHORT
								).show();
								return;
							}

							if (existing == null) {
								publishTip(title, summary, body, category);
							} else {
								updateTip(existing.getTipId(), title, summary, body, category);
							}
						}
				)
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void publishTip(String title, String summary, String body, String category) {
		String tipId = "tip_" + UUID.randomUUID().toString().substring(0, 8);
		User admin = UserSession.getInstance().getCurrentUser();

		Map<String, Object> data = new HashMap<>();
		data.put("tipId", tipId);
		data.put("title", title);
		data.put("summary", summary);
		data.put("bodyContent", body);
		data.put("category", category);
		data.put("publishedAt", Timestamp.now());
		data.put("viewCount", 0);
		data.put("likeCount", 0);
		data.put("isNew", true);
		data.put("authorAdminId", admin != null ? admin.getUserId() : "admin");

		db.collection("sustainabilityTips").document(tipId)
				.set(data)
				.addOnSuccessListener(a -> {
					Toast.makeText(getContext(), "Tip published!", Toast.LENGTH_SHORT).show();
					loadTips();
				})
				.addOnFailureListener(e ->
						Toast.makeText(
								getContext(),
								"Failed: " + e.getMessage(),
								Toast.LENGTH_SHORT
						).show());
	}

	private void updateTip(
			String tipId,
			String title,
			String summary,
			String body,
			String category
	) {
		Map<String, Object> updates = new HashMap<>();
		updates.put("title", title);
		updates.put("summary", summary);
		updates.put("bodyContent", body);
		updates.put("category", category);
		updates.put("lastEditedAt", Timestamp.now());

		db.collection("sustainabilityTips").document(tipId)
				.update(updates)
				.addOnSuccessListener(a -> {
					Toast.makeText(getContext(), "Tip updated!", Toast.LENGTH_SHORT).show();
					loadTips();
				})
				.addOnFailureListener(e ->
						Toast.makeText(
								getContext(),
								"Failed: " + e.getMessage(),
								Toast.LENGTH_SHORT
						).show());
	}

	private void confirmDelete(SustainabilityTip tip) {
		new AlertDialog.Builder(getContext())
				.setTitle("Delete Tip")
				.setMessage("Delete \"" + tip.getTitle() + "\"? This cannot be undone.")
				.setPositiveButton(
						"Delete", (d, w) -> {
							db.collection("sustainabilityTips")
									.document(tip.getTipId())
									.delete()
									.addOnSuccessListener(a -> {
										Toast.makeText(
												getContext(),
												"Tip deleted.",
												Toast.LENGTH_SHORT
										).show();
										loadTips();
									});
						}
				)
				.setNegativeButton("Cancel", null)
				.show();
	}

	private int dpToPx(int dp) {
		float density = getResources().getDisplayMetrics().density;
		return Math.round(dp * density);
	}
}
