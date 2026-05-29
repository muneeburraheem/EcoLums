package com.example.ecolums;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full-screen detail view for a single club.
 * - Club leader sees the invite code, QR, and member management.
 * - Members see the member list and can leave.
 * - Non-members see club info only.
 */
public class ClubDetailActivity extends AppCompatActivity {

	public static final String EXTRA_CLUB_ID = "club_id";

	private TextView tvClubName, tvClubDesc, tvMemberCount, tvTotalPoints;
	private TextView tvInviteCode;
	private ImageView imgQrCode;
	private CardView cardInvite;
	private LinearLayout llMembers;
	private TextView tvNoMembers;
	private MaterialButton btnLeave, btnCopyLink, btnDeactivate, btnTeamDashboard, btnEnrollTeam;
	private FirebaseFirestore db;
	private ChallengeService challengeService;
	private Club loadedClub;
	private String clubDocId;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_club_detail);

		db = FirebaseFirestore.getInstance();
		challengeService = new ChallengeService();

		findViewById(R.id.tv_back).setOnClickListener(v -> finish());

		tvClubName = findViewById(R.id.tv_club_name);
		tvClubDesc = findViewById(R.id.tv_club_description);
		tvMemberCount = findViewById(R.id.tv_member_count);
		tvTotalPoints = findViewById(R.id.tv_total_points);
		tvInviteCode = findViewById(R.id.tv_invite_code);
		imgQrCode = findViewById(R.id.img_qr_code);
		cardInvite = findViewById(R.id.card_invite);
		llMembers = findViewById(R.id.ll_members);
		tvNoMembers = findViewById(R.id.tv_no_members);
		btnLeave         = findViewById(R.id.btn_leave_club);
		btnCopyLink      = findViewById(R.id.btn_copy_link);
		btnDeactivate    = findViewById(R.id.btn_deactivate);
		btnTeamDashboard = findViewById(R.id.btn_team_dashboard);
		btnEnrollTeam    = findViewById(R.id.btn_enroll_team);

		clubDocId = getIntent().getStringExtra(EXTRA_CLUB_ID);
		if (clubDocId == null) {
			finish();
			return;
		}

		loadClub(clubDocId);
	}

	private void loadClub(String clubId) {
		db.collection("clubs").document(clubId)
				.get()
				.addOnSuccessListener(doc -> {
					if (!doc.exists()) {
						finish();
						return;
					}
					Club c = doc.toObject(Club.class);
					if (c == null) {
						finish();
						return;
					}
					c.setClubId(doc.getId());
					loadedClub = c;
					populateUI(c);
				})
				.addOnFailureListener(e -> {
					Toast.makeText(this, "Failed to load club.", Toast.LENGTH_SHORT).show();
					finish();
				});
	}

	private void populateUI(Club c) {
		tvClubName.setText(c.getName() != null ? c.getName() : "Club");
		tvClubDesc.setText(c.getDescription() != null ? c.getDescription() : "");

		int memberCount = c.getMemberIds() != null ? c.getMemberIds().size() : 0;
		tvMemberCount.setText(String.valueOf(memberCount));
		tvTotalPoints.setText(String.format(Locale.getDefault(), "%.0f", c.getTotalPoints()));

		User currentUser = UserSession.getInstance().getCurrentUser();
		boolean isLeader = currentUser != null
				&& currentUser.getUserId().equals(c.getLeaderId());
		boolean isMember = currentUser != null
				&& c.getMemberIds() != null
				&& c.getMemberIds().contains(currentUser.getUserId());

		// Show invite section to leader
		if (isLeader && c.getJoinCode() != null && c.isJoinCodeActive()) {
			cardInvite.setVisibility(View.VISIBLE);
			tvInviteCode.setText(c.getJoinCode());
			generateAndShowQR(c.getJoinCode());
		}

		// Copy link and deactivate for leader
		if (btnCopyLink != null) {
			btnCopyLink.setOnClickListener(v -> {
				if (c.getInviteLink() != null) {
					ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
					cm.setPrimaryClip(ClipData.newPlainText("Club Invite", c.getInviteLink()));
					Toast.makeText(this, "Link copied!", Toast.LENGTH_SHORT).show();
				}
			});
		}

		if (btnDeactivate != null) {
			btnDeactivate.setOnClickListener(v -> {
				db.collection("clubs").document(clubDocId)
						.update("joinCodeActive", false)
						.addOnSuccessListener(unused -> {
							Toast.makeText(this, "Invite deactivated.", Toast.LENGTH_SHORT).show();
							cardInvite.setVisibility(View.GONE);
						})
						.addOnFailureListener(e ->
								Toast.makeText(this, "Deactivated.", Toast.LENGTH_SHORT).show());
			});
		}

		// Team Dashboard — visible to all members
		if (isMember && btnTeamDashboard != null) {
			btnTeamDashboard.setVisibility(View.VISIBLE);
			btnTeamDashboard.setOnClickListener(v -> {
				Intent intent = new Intent(this, TeamDashboardActivity.class);
				intent.putExtra(TeamDashboardActivity.EXTRA_CLUB_ID, clubDocId);
				startActivity(intent);
			});
		}

		// Enroll Team — visible only to leader
		if (isLeader && btnEnrollTeam != null) {
			btnEnrollTeam.setVisibility(View.VISIBLE);
			btnEnrollTeam.setOnClickListener(v -> showEnrollChallengeDialog(c));
		}

		// Leave button for members who are not the leader
		if (isMember && !isLeader) {
			btnLeave.setVisibility(View.VISIBLE);
			btnLeave.setOnClickListener(v -> leaveClub());
		}

		// Load member names and display them
		loadMembers(c.getMemberIds(), c.getLeaderId());
	}

	private void generateAndShowQR(String code) {
		String link = "https://ecolums.app/club?code=" + code;
		try {
			Bitmap bmp = challengeService.generateQRCode(link);
			imgQrCode.setImageBitmap(bmp);
		} catch (Exception e) {
			imgQrCode.setVisibility(View.GONE);
		}
	}

	private void loadMembers(List<String> memberIds, String leaderId) {
		if (memberIds == null || memberIds.isEmpty()) {
			tvNoMembers.setVisibility(View.VISIBLE);
			return;
		}

		llMembers.removeAllViews();

		for (String memberId : memberIds) {
			db.collection("users").document(memberId)
					.get()
					.addOnSuccessListener(doc -> {
						String name = memberId; // fallback to UID
						if (doc.exists() && doc.getString("name") != null) {
							name = doc.getString("name");
						}
						addMemberRow(name, memberId, memberId.equals(leaderId));
					})
					.addOnFailureListener(e -> addMemberRow(
							memberId,
							memberId,
							memberId.equals(leaderId)
					));
		}
	}

	private void addMemberRow(String name, String memberId, boolean isLeader) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
		);
		rowParams.setMargins(0, 0, 0, dpToPx(12));
		row.setLayoutParams(rowParams);

		// Avatar emoji
		TextView tvAvatar = new TextView(this);
		tvAvatar.setText(isLeader ? "👑" : "👤");
		tvAvatar.setTextSize(22);
		LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
				dpToPx(40),
				dpToPx(40)
		);
		avatarParams.setMarginEnd(dpToPx(12));
		tvAvatar.setLayoutParams(avatarParams);
		tvAvatar.setGravity(Gravity.CENTER);

		// Name column
		LinearLayout nameCol = new LinearLayout(this);
		nameCol.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams nameColParams = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		nameCol.setLayoutParams(nameColParams);

		TextView tvName = new TextView(this);
		tvName.setText(name);
		tvName.setTextSize(14);
		tvName.setTextColor(Color.parseColor("#212121"));
		tvName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

		nameCol.addView(tvName);

		if (isLeader) {
			TextView tvRole = new TextView(this);
			tvRole.setText("Club Leader");
			tvRole.setTextSize(11);
			tvRole.setTextColor(Color.parseColor("#009688")); // teal
			tvRole.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
			nameCol.addView(tvRole);
		}

		row.addView(tvAvatar);
		row.addView(nameCol);

		// Divider
		View divider = new View(this);
		divider.setBackgroundColor(Color.parseColor("#F0F0F0"));
		LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));

		LinearLayout wrapper = new LinearLayout(this);
		wrapper.setOrientation(LinearLayout.VERTICAL);
		wrapper.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		wrapper.addView(row);
		wrapper.addView(divider, divParams);

		runOnUiThread(() -> llMembers.addView(wrapper));
	}

	private void leaveClub() {
		User user = UserSession.getInstance().getCurrentUser();
		if (user == null) return;

		btnLeave.setEnabled(false);
		btnLeave.setText("Leaving…");

		user.leaveClub(success -> runOnUiThread(() -> {
			if (success) {
				Toast.makeText(this, "You've left the club.", Toast.LENGTH_SHORT).show();
				finish();
			} else {
				btnLeave.setEnabled(true);
				btnLeave.setText("Leave Club");
				Toast.makeText(this, "Failed to leave. Try again.", Toast.LENGTH_SHORT).show();
			}
		}));
	}

	private void showEnrollChallengeDialog(Club club) {
		db.collection("challenges")
				.whereEqualTo("status", Challenge.STATUS_ACTIVE)
				.get()
				.addOnSuccessListener(snap -> {
					if (snap.isEmpty()) {
						Toast.makeText(this, "No active challenges available.", Toast.LENGTH_SHORT).show();
						return;
					}
					List<String> ids   = new ArrayList<>();
					List<String> names = new ArrayList<>();
					for (var doc : snap.getDocuments()) {
						ids.add(doc.getId());
						String t = doc.getString("title");
						names.add(t != null ? t : doc.getId());
					}
					new AlertDialog.Builder(this)
							.setTitle("Enroll Team in Challenge")
							.setItems(names.toArray(new String[0]), (d, which) ->
									enrollTeamInChallenge(club, ids.get(which), names.get(which)))
							.setNegativeButton("Cancel", null)
							.show();
				})
				.addOnFailureListener(e ->
						Toast.makeText(this, "Failed to load challenges.", Toast.LENGTH_SHORT).show());
	}

	private void enrollTeamInChallenge(Club club, String challengeId, String challengeTitle) {
		List<String> members = club.getMemberIds();
		if (members == null || members.isEmpty()) {
			Toast.makeText(this, "Club has no members.", Toast.LENGTH_SHORT).show();
			return;
		}

		WriteBatch batch = db.batch();
		var challengeRef = db.collection("challenges").document(challengeId);

		// Add all members to participantIds and initialize their scores
		batch.update(challengeRef, "participantIds", FieldValue.arrayUnion(members.toArray()));
		batch.update(challengeRef, "teamIds", FieldValue.arrayUnion(clubDocId));

		Map<String, Object> scoreUpdates = new HashMap<>();
		for (String memberId : members) {
			scoreUpdates.put("participantScores." + memberId, 0.0);
		}
		// Firestore update with dot-notation map
		for (Map.Entry<String, Object> entry : scoreUpdates.entrySet()) {
			batch.update(challengeRef, entry.getKey(), entry.getValue());
		}

		batch.commit()
				.addOnSuccessListener(unused ->
						Toast.makeText(this,
								"Team enrolled in \"" + challengeTitle + "\"!",
								Toast.LENGTH_SHORT).show())
				.addOnFailureListener(e ->
						Toast.makeText(this, "Enrollment failed.", Toast.LENGTH_SHORT).show());
	}

	private int dpToPx(int dp) {
		return (int) (dp * getResources().getDisplayMetrics().density);
	}
}
