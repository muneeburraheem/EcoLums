package com.example.ecolums;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin tab 1 — User Management.
 * <p>
 * Satisfies:
 * AC 1 — Only reachable through AdminDashboardActivity which enforces ROLE_ADMIN.
 * AC 2 — "Reset Password" sends a Firebase Auth password-reset email.
 * AC 4 — "Delete Data" permanently removes all activityLogs for a user.
 * "Export Data" serialises the user profile to JSON shown in a dialog.
 * AC 5 — Every action writes an entry to the Firestore auditLog collection.
 */
public class AdminUsersFragment extends Fragment {

	private RecyclerView rvUsers;
	private TextView tvUserCount;
	private SwipeRefreshLayout swipeRefresh;
	private TextInputEditText etSearch;
	private UserManagementAdapter adapter;
	private final List<User> allUsers = new ArrayList<>();
	private final List<User> filteredUsers = new ArrayList<>();
	private FirebaseFirestore db;
	private FirebaseAuth auth;

	@Nullable
	@Override
	public View onCreateView(
			@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState
	) {
		View view = inflater.inflate(R.layout.fragment_admin_users, container, false);

		db = FirebaseFirestore.getInstance();
		auth = FirebaseAuth.getInstance();

		rvUsers = view.findViewById(R.id.rv_users);
		tvUserCount = view.findViewById(R.id.tv_user_count);
		swipeRefresh = view.findViewById(R.id.swipe_refresh_users);
		etSearch = view.findViewById(R.id.et_search_users);

		adapter = new UserManagementAdapter(
				filteredUsers,
				this::onResetPassword,
				this::onDeleteData,
				this::onExportData
		);

		rvUsers.setLayoutManager(new LinearLayoutManager(getContext()));
		rvUsers.setAdapter(adapter);

		swipeRefresh.setOnRefreshListener(this::loadUsers);
		swipeRefresh.setColorSchemeResources(R.color.teal_primary);

		etSearch.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				filterUsers(s.toString());
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		loadUsers();
		return view;
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Data loading
	// ─────────────────────────────────────────────────────────────────────────

	private void loadUsers() {
		db.collection("users")
				.get()
				.addOnSuccessListener(querySnapshot -> {
					allUsers.clear();
					for (var doc : querySnapshot.getDocuments()) {
						User u = doc.toObject(User.class);
						if (u != null) allUsers.add(u);
					}
					filteredUsers.clear();
					filteredUsers.addAll(allUsers);
					adapter.notifyDataSetChanged();
					tvUserCount.setText(allUsers.size() + " registered users");
					swipeRefresh.setRefreshing(false);
				})
				.addOnFailureListener(e -> {
					swipeRefresh.setRefreshing(false);
					Toast.makeText(
							getContext(), "Failed to load users: " + e.getMessage(),
							Toast.LENGTH_SHORT
					).show();
				});
	}

	private void filterUsers(String query) {
		filteredUsers.clear();
		if (query.isEmpty()) {
			filteredUsers.addAll(allUsers);
		} else {
			String lower = query.toLowerCase(Locale.getDefault());
			for (User u : allUsers) {
				if (u.getEmail() != null
						&& u.getEmail().toLowerCase(Locale.getDefault()).contains(lower)) {
					filteredUsers.add(u);
				}
			}
		}
		adapter.notifyDataSetChanged();
	}

	// ─────────────────────────────────────────────────────────────────────────
	// AC 2 — Password reset
	// ─────────────────────────────────────────────────────────────────────────

	private void onResetPassword(User targetUser) {
		if (targetUser.getEmail() == null) return;

		new AlertDialog.Builder(requireContext())
				.setTitle("Reset Password")
				.setMessage("Send a password-reset email to:\n" + targetUser.getEmail() + "?")
				.setPositiveButton(
						"Send", (dialog, which) -> {
							auth.sendPasswordResetEmail(targetUser.getEmail())
									.addOnSuccessListener(aVoid -> {
										Toast.makeText(
												getContext(),
												"Reset email sent to " + targetUser.getEmail(),
												Toast.LENGTH_SHORT
										).show();
										// AC 5 — Audit log
										writeAuditEntry(
												AuditLogEntry.ACTION_PASSWORD_RESET,
												targetUser.getUserId(),
												"Password reset email sent to " + targetUser.getEmail(),
												null, null
										);
									})
									.addOnFailureListener(e ->
											Toast.makeText(
													getContext(),
													"Failed: " + e.getMessage(), Toast.LENGTH_SHORT
											).show());
						}
				)
				.setNegativeButton("Cancel", null)
				.show();
	}

	// ─────────────────────────────────────────────────────────────────────────
	// AC 4 — Delete user's sustainability data
	// ─────────────────────────────────────────────────────────────────────────

	private void onDeleteData(User targetUser) {
		new AlertDialog.Builder(requireContext())
				.setTitle("⚠ Delete User Data")
				.setMessage("This will permanently delete ALL activity logs for:\n"
						+ targetUser.getEmail()
						+ "\n\nThis cannot be undone. Continue?")
				.setPositiveButton(
						"Delete", (dialog, which) -> {
							// AC 5 — write audit BEFORE deletion so the record exists
							writeAuditEntry(
									AuditLogEntry.ACTION_USER_DATA_DELETED,
									targetUser.getUserId(),
									"Admin deleted all activity logs for " + targetUser.getEmail(),
									null, null
							);

							// Delete all activityLogs — collect tasks and wait for all to finish
							db.collection("users")
									.document(targetUser.getUserId())
									.collection("activityLogs")
									.get()
									.addOnSuccessListener(querySnapshot -> {
										List<Task<Void>> deleteTasks = new ArrayList<>();
										for (var doc : querySnapshot.getDocuments()) {
											deleteTasks.add(doc.getReference().delete());
										}
										Tasks.whenAll(deleteTasks)
												.addOnSuccessListener(aVoid -> {
													// All logs deleted — now reset the user document
													db.collection("users")
															.document(targetUser.getUserId())
															.update(
																	"totalPoints",
																	0.0,
																	"earnedBadgeIds",
																	new ArrayList<>()
															)
															.addOnSuccessListener(u -> {
																targetUser.setTotalPoints(0);
																adapter.notifyDataSetChanged();
																Toast.makeText(
																		getContext(),
																		"All data deleted for " + targetUser.getEmail(),
																		Toast.LENGTH_SHORT
																).show();
															})
															.addOnFailureListener(e ->
																	Toast.makeText(
																			getContext(),
																			"Logs deleted but points reset failed: " + e.getMessage(),
																			Toast.LENGTH_SHORT
																	).show());
												})
												.addOnFailureListener(e ->
														Toast.makeText(
																getContext(),
																"Delete failed: " + e.getMessage(),
																Toast.LENGTH_SHORT
														).show());
									})
									.addOnFailureListener(e ->
											Toast.makeText(
													getContext(),
													"Delete failed: " + e.getMessage(),
													Toast.LENGTH_SHORT
											).show());
						}
				)
				.setNegativeButton("Cancel", null)
				.show();
	}

	// ─────────────────────────────────────────────────────────────────────────
	// AC 4 — Export user data as JSON
	// ─────────────────────────────────────────────────────────────────────────

	private void onExportData(User targetUser) {
		// Fetch all activity logs then serialise everything to JSON
		db.collection("users")
				.document(targetUser.getUserId())
				.collection("activityLogs")
				.get()
				.addOnSuccessListener(querySnapshot -> {
					List<Map<String, Object>> logList = new ArrayList<>();
					for (var doc : querySnapshot.getDocuments()) {
						logList.add(doc.getData());
					}

					Map<String, Object> exportPayload = new HashMap<>();
					exportPayload.put("userId", targetUser.getUserId());
					exportPayload.put("email", targetUser.getEmail());
					exportPayload.put("role", targetUser.getRole());
					exportPayload.put("totalPoints", targetUser.getTotalPoints());
					exportPayload.put("activityLogs", logList);

					String json = new Gson().toJson(exportPayload);

					// AC 5 — Audit log
					writeAuditEntry(
							AuditLogEntry.ACTION_USER_DATA_EXPORTED,
							targetUser.getUserId(),
							"Admin exported data for " + targetUser.getEmail(),
							null, null
					);

					// Show JSON in a scrollable dialog (prototype — production would write to file)
					new AlertDialog.Builder(requireContext())
							.setTitle("Exported Data — " + targetUser.getEmail())
							.setMessage(json)
							.setPositiveButton("OK", null)
							.show();
				})
				.addOnFailureListener(e ->
						Toast.makeText(
								getContext(), "Export failed: " + e.getMessage(),
								Toast.LENGTH_SHORT
						).show());
	}

	// ─────────────────────────────────────────────────────────────────────────
	// AC 5 — Audit log writer
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Writes an immutable audit log entry to Firestore.
	 * Called after every privileged action to satisfy AC 5.
	 *
	 * @param actionType     One of AuditLogEntry.ACTION_* constants.
	 * @param targetEntityId UID of the affected user (may be null).
	 * @param description    Human-readable description.
	 * @param previousValue  Old value serialised as string (may be null).
	 * @param newValue       New value serialised as string (may be null).
	 */
	private void writeAuditEntry(
			String actionType, String targetEntityId,
			String description, String previousValue, String newValue
	) {
		if (auth.getCurrentUser() == null) return;

		Map<String, Object> entry = new HashMap<>();
		entry.put("adminId", auth.getCurrentUser().getUid());
		entry.put("adminEmail", auth.getCurrentUser().getEmail());
		entry.put("actionType", actionType);
		entry.put("targetEntityId", targetEntityId);
		entry.put("description", description);
		entry.put("previousValue", previousValue);
		entry.put("newValue", newValue);
		entry.put("timestamp", Timestamp.now());

		// Fire-and-forget — audit writes never block the UI
		db.collection("auditLog").add(entry);
	}
}
