package com.example.ecolums;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

/**
 * Admin landing tab for campus-wide reporting tools.
 */
public class AdminImpactFragment extends Fragment {

	@Nullable
	@Override
	public View onCreateView(
			@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState
	) {
		LinearLayout root = new LinearLayout(requireContext());
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp(16), dp(16), dp(16), dp(16));
		root.setBackgroundColor(Color.parseColor("#F5F7F7"));

		TextView title = new TextView(requireContext());
		title.setText("Campus Sustainability Reporting");
		title.setTextSize(18);
		title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
		title.setTextColor(Color.parseColor("#212121"));
		root.addView(title);

		TextView body = new TextView(requireContext());
		body.setText("Open the executive dashboard to review campus totals, trends, goals, departmental breakdowns, and exports.");
		body.setTextSize(13);
		body.setTextColor(Color.parseColor("#607D8B"));
		body.setPadding(0, dp(8), 0, dp(18));
		root.addView(body);

		MaterialButton open = new MaterialButton(requireContext());
		open.setText("Open Executive Dashboard");
		open.setTextColor(Color.WHITE);
		open.setBackgroundColor(Color.parseColor("#009688"));
		open.setOnClickListener(v -> startActivity(new Intent(getContext(), CampusImpactActivity.class)));
		root.addView(open, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

		return root;
	}

	private int dp(int value) {
		return (int) (value * getResources().getDisplayMetrics().density);
	}
}
