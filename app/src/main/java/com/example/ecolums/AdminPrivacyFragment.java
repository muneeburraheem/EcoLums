package com.example.ecolums;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin tab 2 — Global Privacy Defaults.
 * <p>
 * Satisfies AC 3: Admin can toggle whether new users share data with clubs
 * by default. The setting is persisted to platformConfig/privacySettings
 * and an audit log entry is written on every save.
 */
public class AdminPrivacyFragment extends Fragment {

	private SwitchMaterial switchShareClubs, switchLeaderboard;
	private MaterialButton btnSave, btnResetDatabase;
	private TextView tvStatus;
	private FirebaseFirestore db;
	private FirebaseAuth auth;

	@Nullable
	@Override
	public View onCreateView(
			@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState
	) {
		View view = inflater.inflate(R.layout.fragment_admin_privacy, container, false);

		db = FirebaseFirestore.getInstance();
		auth = FirebaseAuth.getInstance();

		switchShareClubs = view.findViewById(R.id.switch_share_clubs);
		switchLeaderboard = view.findViewById(R.id.switch_leaderboard_visible);
		btnSave = view.findViewById(R.id.btn_save_privacy);
		tvStatus = view.findViewById(R.id.tv_privacy_status);
		btnResetDatabase = view.findViewById(R.id.btn_reset_database);

		loadCurrentDefaults();

		btnSave.setOnClickListener(v -> saveDefaults());
		btnResetDatabase.setOnClickListener(v -> confirmReset());
		return view;
	}

	/**
	 * Reads the current privacy defaults from Firestore and populates the toggles.
	 */
	private void loadCurrentDefaults() {
		db.collection("platformConfig").document("privacySettings")
				.get()
				.addOnSuccessListener(doc -> {
					if (!doc.exists()) return;
					Boolean shareClubs = doc.getBoolean("shareDataWithClubsByDefault");
					Boolean showLeader = doc.getBoolean("showOnLeaderboardByDefault");
					if (shareClubs != null) switchShareClubs.setChecked(shareClubs);
					if (showLeader != null) switchLeaderboard.setChecked(showLeader);
				});
	}

	/**
	 * Persists the current toggle state to Firestore and writes an audit entry.
	 * Satisfies AC 3 and AC 5.
	 */
	private void saveDefaults() {
		boolean shareByDefault = switchShareClubs.isChecked();
		boolean showOnLeader = switchLeaderboard.isChecked();

		Map<String, Object> config = new HashMap<>();
		config.put("shareDataWithClubsByDefault", shareByDefault);
		config.put("showOnLeaderboardByDefault", showOnLeader);
		config.put("lastUpdated", Timestamp.now());

		db.collection("platformConfig").document("privacySettings")
				.set(config)
				.addOnSuccessListener(aVoid -> {
					tvStatus.setText("✓ Privacy defaults saved.");
					tvStatus.setVisibility(View.VISIBLE);

					// AC 5 — Audit log
					if (auth.getCurrentUser() == null) return;
					Map<String, Object> auditEntry = new HashMap<>();
					auditEntry.put("adminId", auth.getCurrentUser().getUid());
					auditEntry.put("adminEmail", auth.getCurrentUser().getEmail());
					auditEntry.put("actionType", AuditLogEntry.ACTION_USER_PRIVACY_CHANGED);
					auditEntry.put("targetEntityId", "GLOBAL");
					auditEntry.put(
							"description",
							"Global privacy defaults updated: shareWithClubs="
									+ shareByDefault + ", showOnLeaderboard=" + showOnLeader
					);
					auditEntry.put(
							"newValue",
							"shareWithClubs=" + shareByDefault
									+ " | showOnLeaderboard=" + showOnLeader
					);
					auditEntry.put("timestamp", Timestamp.now());
					db.collection("auditLog").add(auditEntry);
				})
				.addOnFailureListener(e ->
						tvStatus.setText("✗ Save failed: " + e.getMessage()));

		tvStatus.setVisibility(View.VISIBLE);
	}

	private void confirmReset() {
		new AlertDialog.Builder(requireContext())
				.setTitle("Reset All Data?")
				.setMessage(
						"This will permanently delete all challenges, users, tips, and audit logs from Firestore. This cannot be undone.")
				.setPositiveButton("Reset", (dialog, which) -> resetDatabase())
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void resetDatabase() {
		String[] collections = {
				"challenges",
				"users",
				"sustainabilityTips",
				"campusGoals",
				"auditLog"
		};
		for (String col : collections) {
			db.collection(col).get().addOnSuccessListener(snapshot -> {
				for (QueryDocumentSnapshot doc : snapshot) {
					doc.getReference().delete();
				}
			});
		}
		// Clear seeded sentinel so FirebaseSeeder doesn't think data already exists
		db.collection("seeded").document("status").delete();

		Toast.makeText(getContext(), "Database reset. All data deleted.", Toast.LENGTH_LONG).show();
	}
}
