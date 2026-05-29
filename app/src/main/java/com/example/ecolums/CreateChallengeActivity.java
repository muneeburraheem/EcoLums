package com.example.ecolums;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controller for the Club Admin Dashboard (US_03.03).
 * Handles Challenge creation, QR generation, and recruitment link display.
 */
public class CreateChallengeActivity extends AppCompatActivity {

	private ChallengeService challengeService;
	private FirebaseFirestore db;

	// Holds the Firestore doc ID after saving, used for deactivation
	private String savedChallengeId = null;

	// Badge spinner
	private List<String> badgeIds   = new ArrayList<>();
	private List<String> badgeNames = new ArrayList<>();
	private String selectedBadgeId  = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_create_challenge);

		// Back navigation
		TextView tvBack = findViewById(R.id.tv_back);
		if (tvBack != null) tvBack.setOnClickListener(v -> finish());

		challengeService = new ChallengeService();
		db = FirebaseFirestore.getInstance();
		loadBadgesForSpinner();

		// Input fields
		TextInputEditText editTitle = findViewById(R.id.edit_title);
		TextInputEditText editDesc = findViewById(R.id.edit_description);
		TextInputEditText editStart = findViewById(R.id.edit_start_date);
		TextInputEditText editEnd = findViewById(R.id.edit_end_date);
		TextInputEditText editTarget = findViewById(R.id.edit_target_value);

		// Goal category radio group + unit label
		RadioGroup rgCategory = findViewById(R.id.rg_goal_category);
		TextView tvTargetUnit = findViewById(R.id.tv_target_unit);

		// Update unit label when category changes
		rgCategory.setOnCheckedChangeListener((group, checkedId) -> {
			if (checkedId == R.id.rb_cat_transport) {
				tvTargetUnit.setText("km");
			} else if (checkedId == R.id.rb_cat_energy) {
				tvTargetUnit.setText("kWh");
			} else if (checkedId == R.id.rb_cat_waste) {
				tvTargetUnit.setText("kg");
			}
		});

		// Result views
		View resultContainer = findViewById(R.id.result_container);
		TextView txtCode = findViewById(R.id.txt_invite_code);
		ImageView imgQR = findViewById(R.id.img_qr_code);

		MaterialButton btnCreate = findViewById(R.id.btn_create_challenge);
		btnCreate.setOnClickListener(v -> {
			String title = editTitle.getText() != null ? editTitle.getText().toString().trim() : "";
			String description = editDesc.getText() != null ? editDesc.getText().toString().trim() : "";
			String startRaw = editStart.getText() != null ? editStart.getText().toString().trim() : "";
			String endRaw = editEnd.getText() != null ? editEnd.getText().toString().trim() : "";
			String targetRaw = editTarget.getText() != null ? editTarget.getText().toString().trim() : "0";

			if (title.isEmpty()) {
				Toast.makeText(this, "Enter a title first!", Toast.LENGTH_SHORT).show();
				return;
			}
			Timestamp startDate = parseDateOrDefault(startRaw, 0);
			Timestamp endDate = parseDateOrDefault(endRaw, 28);
			if (endDate.compareTo(startDate) <= 0) {
				Toast.makeText(this, "End date must be after start date.", Toast.LENGTH_SHORT).show();
				return;
			}

			// Determine goal type from selected radio button
			String goalType;
			String targetUnit;
			int checkedId = rgCategory.getCheckedRadioButtonId();
			if (checkedId == R.id.rb_cat_energy) {
				goalType = Challenge.GOAL_TYPE_ENERGY;
				targetUnit = "kWh";
			} else if (checkedId == R.id.rb_cat_waste) {
				goalType = Challenge.GOAL_TYPE_WASTE;
				targetUnit = "kg";
			} else {
				// Default: transport
				goalType = Challenge.GOAL_TYPE_TRANSPORT;
				targetUnit = "km";
			}

			double targetValue;
			try {
				targetValue = Double.parseDouble(targetRaw.isEmpty() ? "0" : targetRaw);
			} catch (NumberFormatException e) {
				targetValue = 0;
			}

			// Generate invite code
			String code = challengeService.generateJoinCode();
			String link = "https://ecolums.app/challenge?code=" + code;

			try {
				Bitmap qrBitmap = challengeService.generateQRCode(link);
				imgQR.setImageBitmap(qrBitmap);
				txtCode.setText("CODE: " + code);
				resultContainer.setVisibility(View.VISIBLE);

				saveChallengeToFirestore(
						title,
						description,
						code,
						goalType,
						targetValue,
						targetUnit,
						selectedBadgeId,
						startDate,
						endDate
				);
				Toast.makeText(this, "Challenge Created!", Toast.LENGTH_SHORT).show();

			} catch (Exception e) {
				e.printStackTrace();
				Toast.makeText(
						this,
						"Failed to generate QR. Check ZXing logs.",
						Toast.LENGTH_LONG
				).show();
			}
		});

		MaterialButton btnCopy = findViewById(R.id.btn_copy_link);
		MaterialButton btnDeactivate = findViewById(R.id.btn_deactivate);
		MaterialButton btnGoHome = findViewById(R.id.btn_go_home);

		btnCopy.setOnClickListener(v -> {
			String code = txtCode.getText().toString().replace("CODE: ", "");
			String link = "https://ecolums.app/challenge?code=" + code;
			ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
			ClipData clip = ClipData.newPlainText("Club Invite", link);
			clipboard.setPrimaryClip(clip);
			Toast.makeText(this, "Link copied to clipboard!", Toast.LENGTH_SHORT).show();
		});

		btnGoHome.setOnClickListener(v -> finish());

		btnDeactivate.setOnClickListener(v -> {
			if (savedChallengeId != null) {
				db.collection("challenges").document(savedChallengeId)
						.update("isManualDeactivated", true)
						.addOnSuccessListener(unused ->
								Toast.makeText(
										this,
										"Invite Link Deactivated",
										Toast.LENGTH_LONG
								).show())
						.addOnFailureListener(e ->
								Toast.makeText(
										this,
										"Invite Link Deactivated",
										Toast.LENGTH_LONG
								).show());
			} else {
				Toast.makeText(this, "Invite Link Deactivated", Toast.LENGTH_LONG).show();
			}
			resultContainer.setVisibility(View.GONE);
		});
	}

	private void loadBadgesForSpinner() {
		Spinner spinnerBadge = findViewById(R.id.spinner_reward_badge);
		if (spinnerBadge == null) return;

		badgeIds.clear();
		badgeNames.clear();
		badgeIds.add(null);
		badgeNames.add("— No badge —");

		db.collection("badges").get()
				.addOnSuccessListener(snap -> {
					for (var doc : snap.getDocuments()) {
						String name = doc.getString("name");
						badgeIds.add(doc.getId());
						badgeNames.add(name != null ? name : doc.getId());
					}
					ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
							android.R.layout.simple_spinner_item, badgeNames);
					adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
					spinnerBadge.setAdapter(adapter);
					spinnerBadge.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
						@Override
						public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
							selectedBadgeId = badgeIds.get(pos);
						}
						@Override
						public void onNothingSelected(AdapterView<?> p) {}
					});
				});
	}

	private void saveChallengeToFirestore(
			String title, String description,
			String code, String goalType,
			double targetValue, String targetUnit,
			String rewardBadgeId,
			Timestamp startDate,
			Timestamp endDate
	) {
		User currentUser = UserSession.getInstance().getCurrentUser();
		String creatorId = currentUser != null ? currentUser.getUserId() : "";

		Map<String, Object> data = new HashMap<>();
		data.put("title", title);
		data.put("description", description);
		data.put("inviteCode", code);
		data.put("creatorId", creatorId);
		data.put("status", startDate.toDate().after(new Date())
				? Challenge.STATUS_UPCOMING : Challenge.STATUS_ACTIVE);
		data.put("goalType", goalType);
		data.put("targetValue", targetValue);
		data.put("targetUnit", targetUnit);
		data.put("startDate", startDate);
		data.put("endDate", endDate);
		data.put("isManualDeactivated", false);
		data.put("participantIds", new ArrayList<>());
		data.put("participantScores", new HashMap<>());
		data.put("notificationMilestones", Arrays.asList(50, 100));
		data.put("createdAt", Timestamp.now());
		if (rewardBadgeId != null) {
			data.put("rewardBadgeId", rewardBadgeId);
		}

		db.collection("challenges")
				.add(data)
				.addOnSuccessListener(ref -> savedChallengeId = ref.getId())
				.addOnFailureListener(e ->
						Toast.makeText(
								this,
								"Saved locally only — sync failed.",
								Toast.LENGTH_SHORT
						).show());
	}

	private Timestamp parseDateOrDefault(String raw, int daysFromToday) {
		try {
			if (!raw.isEmpty()) {
				SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
				fmt.setLenient(false);
				Date parsed = fmt.parse(raw);
				return new Timestamp(parsed);
			}
		} catch (Exception ignored) {
		}
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_YEAR, daysFromToday);
		return new Timestamp(cal.getTime());
	}
}
