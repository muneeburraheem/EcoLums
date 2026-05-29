package com.example.ecolums;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Allows any student to create a new sustainability club.
 * On creation, generates a 6-digit invite code, shareable link, and QR code.
 */
public class CreateClubActivity extends AppCompatActivity {

	private ChallengeService challengeService;
	private FirebaseFirestore db;
	private String savedClubId = null;
	private String generatedCode = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_create_club);

		TextView tvBack = findViewById(R.id.tv_back);
		if (tvBack != null) tvBack.setOnClickListener(v -> finish());

		challengeService = new ChallengeService();
		db = FirebaseFirestore.getInstance();

		TextInputEditText editName = findViewById(R.id.edit_club_name);
		TextInputEditText editDesc = findViewById(R.id.edit_club_description);
		RadioGroup rgExpiry = findViewById(R.id.rg_expiry);

		View resultContainer = findViewById(R.id.result_container);
		TextView txtCode = findViewById(R.id.txt_club_code);
		ImageView imgQR = findViewById(R.id.img_club_qr);

		MaterialButton btnCreate = findViewById(R.id.btn_create_club);
		btnCreate.setOnClickListener(v -> {
			String name = editName.getText() != null ? editName.getText().toString().trim() : "";
			String desc = editDesc.getText() != null ? editDesc.getText().toString().trim() : "";

			if (name.isEmpty()) {
				Toast.makeText(this, "Enter a club name first!", Toast.LENGTH_SHORT).show();
				return;
			}

			User currentUser = UserSession.getInstance().getCurrentUser();
			if (currentUser == null) {
				Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show();
				return;
			}

			// Determine expiry
			Timestamp expiry = null;
			int checkedExpiry = rgExpiry.getCheckedRadioButtonId();
			if (checkedExpiry == R.id.rb_1day) {
				Calendar cal = Calendar.getInstance();
				cal.add(Calendar.DAY_OF_YEAR, 1);
				expiry = new Timestamp(cal.getTimeInMillis() / 1000, 0);
			} else if (checkedExpiry == R.id.rb_7days) {
				Calendar cal = Calendar.getInstance();
				cal.add(Calendar.DAY_OF_YEAR, 7);
				expiry = new Timestamp(cal.getTimeInMillis() / 1000, 0);
			} else if (checkedExpiry == R.id.rb_30days) {
				Calendar cal = Calendar.getInstance();
				cal.add(Calendar.DAY_OF_YEAR, 30);
				expiry = new Timestamp(cal.getTimeInMillis() / 1000, 0);
			}

			generatedCode = challengeService.generateJoinCode();
			String link = "https://ecolums.app/club?code=" + generatedCode;

			try {
				Bitmap qrBitmap = challengeService.generateQRCode(link);
				imgQR.setImageBitmap(qrBitmap);
				txtCode.setText(generatedCode);
				resultContainer.setVisibility(View.VISIBLE);

				saveClubToFirestore(
						name,
						desc,
						generatedCode,
						link,
						expiry,
						currentUser.getUserId()
				);
				Toast.makeText(this, "Club Created!", Toast.LENGTH_SHORT).show();
			} catch (Exception e) {
				e.printStackTrace();
				Toast.makeText(this, "Failed to generate QR.", Toast.LENGTH_LONG).show();
			}
		});

		MaterialButton btnCopy = findViewById(R.id.btn_copy_club_link);
		MaterialButton btnDeact = findViewById(R.id.btn_deactivate_club);
		MaterialButton btnView = findViewById(R.id.btn_view_club);
		MaterialButton btnGoBack = findViewById(R.id.btn_go_back_club);

		btnCopy.setOnClickListener(v -> {
			if (generatedCode == null) return;
			String link = "https://ecolums.app/club?code=" + generatedCode;
			ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
			ClipData clip = ClipData.newPlainText("Club Invite Link", link);
			clipboard.setPrimaryClip(clip);
			Toast.makeText(this, "Invite link copied!", Toast.LENGTH_SHORT).show();
		});

		btnDeact.setOnClickListener(v -> {
			if (savedClubId != null) {
				db.collection("clubs").document(savedClubId)
						.update("joinCodeActive", false)
						.addOnSuccessListener(unused ->
								Toast.makeText(
										this,
										"Invite deactivated.",
										Toast.LENGTH_SHORT
								).show())
						.addOnFailureListener(e ->
								Toast.makeText(
										this,
										"Invite deactivated.",
										Toast.LENGTH_SHORT
								).show());
			}
			resultContainer.setVisibility(View.GONE);
		});

		btnView.setOnClickListener(v -> {
			if (savedClubId != null) {
				Intent intent = new Intent(this, ClubDetailActivity.class);
				intent.putExtra(ClubDetailActivity.EXTRA_CLUB_ID, savedClubId);
				startActivity(intent);
			}
		});

		btnGoBack.setOnClickListener(v -> finish());
	}

	private void saveClubToFirestore(
			String name, String description, String code,
			String inviteLink, Timestamp expiry, String leaderId
	) {
		Map<String, Object> data = new HashMap<>();
		data.put("name", name);
		data.put("description", description);
		data.put("leaderId", leaderId);
		data.put("joinCode", code);
		data.put("inviteLink", inviteLink);
		data.put("joinCodeActive", true);
		data.put("joinCodeExpiry", expiry);
		data.put("totalPoints", 0.0);
		data.put(
				"memberIds", new ArrayList<String>() {{
					add(leaderId);
				}}
		);
		data.put("activeChallengeIds", new ArrayList<>());
		data.put("createdAt", Timestamp.now());

		db.collection("clubs")
				.add(data)
				.addOnSuccessListener(ref -> {
					savedClubId = ref.getId();
					// Set the club ID on the user document so they're the leader
					db.collection("users").document(leaderId)
							.update("clubId", savedClubId);
					User currentUser = UserSession.getInstance().getCurrentUser();
					if (currentUser != null) currentUser.setClubId(savedClubId);

					MaterialButton btnView = findViewById(R.id.btn_view_club);
					if (btnView != null) btnView.setVisibility(View.VISIBLE);
				})
				.addOnFailureListener(e ->
						Toast.makeText(
								this,
								"Saved locally only — sync failed.",
								Toast.LENGTH_SHORT
						).show());
	}
}
