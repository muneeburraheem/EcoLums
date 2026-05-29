package com.example.ecolums;

import android.content.Intent;
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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Displays the public challenge gallery and the current user's Eco Score.
 * Users can search challenges and tap Join/Leave to toggle enrolment.
 */
public class ChallengesFragment extends Fragment {

	private RecyclerView rvChallenges;
	private TextView tvChallengePoints;
	private TextInputEditText etSearch;
	private ChallengeAdapter adapter;
	private final List<Challenge> allChallenges = new ArrayList<>();
	private final List<Challenge> filteredList = new ArrayList<>();
	private FirebaseFirestore db;

	@Nullable
	@Override
	public View onCreateView(
			@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState
	) {
		View view = inflater.inflate(R.layout.fragment_challenges, container, false);

		db = FirebaseFirestore.getInstance();

		rvChallenges = view.findViewById(R.id.rv_challenges);
		tvChallengePoints = view.findViewById(R.id.tv_challenge_points);
		etSearch = view.findViewById(R.id.et_search_challenges);

		User user = UserSession.getInstance().getCurrentUser();
		String userId = user != null ? user.getUserId() : null;

		adapter = new ChallengeAdapter(
				filteredList, userId,
				this::onJoinChallenge, this::onLeaveChallenge, this::onChallengeClick
		);
		rvChallenges.setLayoutManager(new LinearLayoutManager(getContext()));
		rvChallenges.setAdapter(adapter);

		// Show current user's eco score
		if (user != null) {
			tvChallengePoints.setText(String.format(
					Locale.getDefault(),
					"%.0f Points", user.getTotalPoints()
			));
		}

		// Show FAB only to CLUB_LEADER and ADMIN
		FloatingActionButton fab = view.findViewById(R.id.fab_create_challenge);
		if (user != null && (User.ROLE_CLUB_LEADER.equals(user.getRole())
				|| User.ROLE_ADMIN.equals(user.getRole()))) {
			fab.setVisibility(View.VISIBLE);
			fab.setOnClickListener(v ->
					startActivity(new Intent(getContext(), CreateChallengeActivity.class)));
		}

		etSearch.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				filterChallenges(s.toString());
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		loadChallenges();
		return view;
	}

	private void loadChallenges() {
		db.collection("challenges")
				.get()
				.addOnSuccessListener(querySnapshot -> {
					allChallenges.clear();
					for (var doc : querySnapshot.getDocuments()) {
						Challenge c = doc.toObject(Challenge.class);
						if (c != null) {
							if (Challenge.STATUS_COMPLETED.equals(c.getStatus())
									|| c.isManualDeactivated()) {
								continue;
							}
							c.setChallengeId(doc.getId());
							allChallenges.add(c);
						}
					}
					filteredList.clear();
					filteredList.addAll(allChallenges);
					adapter.notifyDataSetChanged();
				});
	}

	private void filterChallenges(String query) {
		filteredList.clear();
		if (query.isEmpty()) {
			filteredList.addAll(allChallenges);
		} else {
			String lower = query.toLowerCase(Locale.getDefault());
			for (Challenge c : allChallenges) {
				if (c.getTitle() != null && c.getTitle().toLowerCase(Locale.getDefault()).contains(
						lower)) {
					filteredList.add(c);
				}
			}
		}
		adapter.notifyDataSetChanged();
	}

	private void onJoinChallenge(Challenge challenge) {
		User user = UserSession.getInstance().getCurrentUser();
		if (user == null) return;

		user.joinChallenge(
				challenge.getChallengeId(), success -> {
					if (getActivity() == null) return;
					getActivity().runOnUiThread(() -> {
						if (success) {
							Toast.makeText(
									getContext(),
									"Joined \"" + challenge.getTitle() + "\"!",
									Toast.LENGTH_SHORT
							).show();
							loadChallenges(); // Refresh to reflect updated participantIds
						} else {
							Toast.makeText(
									getContext(),
									"Failed to join. Please try again.", Toast.LENGTH_SHORT
							).show();
						}
					});
				}
		);
	}

	private void onChallengeClick(Challenge challenge) {
		Intent intent = new Intent(getContext(), ChallengeDetailActivity.class);
		intent.putExtra(ChallengeDetailActivity.EXTRA_CHALLENGE_ID, challenge.getChallengeId());
		startActivity(intent);
	}

	private void onLeaveChallenge(Challenge challenge) {
		User user = UserSession.getInstance().getCurrentUser();
		if (user == null) return;

		db.collection("challenges").document(challenge.getChallengeId())
				.update("participantIds", FieldValue.arrayRemove(user.getUserId()))
				.addOnSuccessListener(unused -> {
					if (getActivity() == null) return;
					getActivity().runOnUiThread(() -> {
						Toast.makeText(
								getContext(),
								"Left \"" + challenge.getTitle() + "\"",
								Toast.LENGTH_SHORT
						).show();
						loadChallenges(); // Refresh to reflect updated participantIds
					});
				})
				.addOnFailureListener(e -> {
					if (getActivity() == null) return;
					getActivity().runOnUiThread(() ->
							Toast.makeText(
									getContext(),
									"Failed to leave. Please try again.", Toast.LENGTH_SHORT
							).show());
				});
	}
}
