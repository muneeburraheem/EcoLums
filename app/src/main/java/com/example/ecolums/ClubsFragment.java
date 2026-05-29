package com.example.ecolums;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays two sections:
 * - "My Clubs"  — clubs the current user belongs to (hidden when none)
 * - "All Clubs" — every club in Firestore
 * <p>
 * Tapping any club card opens {@link ClubDetailActivity}.
 */
public class ClubsFragment extends Fragment {

	// All clubs list
	private RecyclerView rvClubs;
	private ClubAdapter allClubsAdapter;
	private final List<Club> allClubs = new ArrayList<>();
	private final List<String> allIds = new ArrayList<>();

	// My clubs list
	private View sectionMyClubs;
	private RecyclerView rvMyClubs;
	private ClubAdapter myClubsAdapter;
	private final List<Club> myClubs = new ArrayList<>();
	private final List<String> myClubIds = new ArrayList<>();

	private TextView tvNoClubs;
	private FirebaseFirestore db;

	@Nullable
	@Override
	public View onCreateView(
			@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState
	) {
		View view = inflater.inflate(R.layout.fragment_clubs, container, false);

		db = FirebaseFirestore.getInstance();

		sectionMyClubs = view.findViewById(R.id.section_my_clubs);
		tvNoClubs = view.findViewById(R.id.tv_no_clubs);

		// My clubs RecyclerView
		rvMyClubs = view.findViewById(R.id.rv_my_clubs);
		myClubsAdapter = new ClubAdapter(myClubs, myClubIds, this::onClubClick);
		rvMyClubs.setLayoutManager(new LinearLayoutManager(getContext()));
		rvMyClubs.setAdapter(myClubsAdapter);

		// All clubs RecyclerView
		rvClubs = view.findViewById(R.id.rv_clubs);
		allClubsAdapter = new ClubAdapter(allClubs, allIds, this::onClubClick);
		rvClubs.setLayoutManager(new LinearLayoutManager(getContext()));
		rvClubs.setAdapter(allClubsAdapter);

		FloatingActionButton fab = view.findViewById(R.id.fab_clubs_action);
		fab.setOnClickListener(v -> showClubActionDialog());

		loadMyClub();
		loadAllClubs();
		return view;
	}

	// ── Data loading ──────────────────────────────────────────────────────

	/**
	 * Fetches the club the current user belongs to (if any) and populates
	 * the "My Clubs" section. Hides the section when the user has no club.
	 */
	private void loadMyClub() {
		User user = UserSession.getInstance().getCurrentUser();
		if (user == null || user.getClubId() == null) {
			sectionMyClubs.setVisibility(View.GONE);
			return;
		}

		db.collection("clubs").document(user.getClubId())
				.get()
				.addOnSuccessListener(doc -> {
					Club c = doc.toObject(Club.class);
					myClubs.clear();
					myClubIds.clear();
					if (c != null) {
						myClubs.add(c);
						myClubIds.add(doc.getId());
						sectionMyClubs.setVisibility(View.VISIBLE);
					} else {
						sectionMyClubs.setVisibility(View.GONE);
					}
					myClubsAdapter.notifyDataSetChanged();
				})
				.addOnFailureListener(e -> sectionMyClubs.setVisibility(View.GONE));
	}

	/**
	 * Fetches every club and populates the "All Clubs" list.
	 */
	private void loadAllClubs() {
		db.collection("clubs")
				.get()
				.addOnSuccessListener(querySnapshot -> {
					allClubs.clear();
					allIds.clear();
					for (var doc : querySnapshot.getDocuments()) {
						Club c = doc.toObject(Club.class);
						if (c != null) {
							allClubs.add(c);
							allIds.add(doc.getId());
						}
					}
					tvNoClubs.setVisibility(allClubs.isEmpty() ? View.VISIBLE : View.GONE);
					allClubsAdapter.notifyDataSetChanged();
				});
	}

	// ── Click handling ────────────────────────────────────────────────────

	private void onClubClick(Club club, String clubId) {
		Intent intent = new Intent(getContext(), ClubDetailActivity.class);
		intent.putExtra(ClubDetailActivity.EXTRA_CLUB_ID, clubId);
		startActivity(intent);
	}

	// ── FAB dialog ────────────────────────────────────────────────────────

	private void showClubActionDialog() {
		User user = UserSession.getInstance().getCurrentUser();
		String[] options;
		if (user != null && user.getClubId() != null) {
			options = new String[] { "View My Club", "Join Another Club with Code" };
		} else {
			options = new String[] { "Create a Club", "Join with 6-Digit Code" };
		}

		new AlertDialog.Builder(requireContext())
				.setTitle("Club Options")
				.setItems(
						options, (dialog, which) -> {
							if (user != null && user.getClubId() != null) {
								if (which == 0) {
									Intent intent = new Intent(
											getContext(),
											ClubDetailActivity.class
									);
									intent.putExtra(
											ClubDetailActivity.EXTRA_CLUB_ID,
											user.getClubId()
									);
									startActivity(intent);
								} else {
									startActivity(new Intent(getContext(), JoinClubActivity.class));
								}
							} else {
								if (which == 0) {
									startActivity(new Intent(
											getContext(),
											CreateClubActivity.class
									));
								} else {
									startActivity(new Intent(getContext(), JoinClubActivity.class));
								}
							}
						}
				)
				.show();
	}

	// ── Lifecycle ─────────────────────────────────────────────────────────

	@Override
	public void onResume() {
		super.onResume();
		loadMyClub();
		loadAllClubs();
	}
}
