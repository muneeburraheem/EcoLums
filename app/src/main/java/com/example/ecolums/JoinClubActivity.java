package com.example.ecolums;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Allows a student to join a club by entering a 6-digit invite code.
 * On success, the user is automatically added to the club's member roster.
 */
public class JoinClubActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_join_club);

		TextView tvBack = findViewById(R.id.tv_back);
		if (tvBack != null) tvBack.setOnClickListener(v -> finish());

		TextInputEditText editCode = findViewById(R.id.edit_join_code);
		MaterialButton btnJoin = findViewById(R.id.btn_join_club);

		btnJoin.setOnClickListener(v -> {
			String code = editCode.getText() != null
					? editCode.getText().toString().trim().toUpperCase()
					: "";

			if (code.length() != 6) {
				Toast.makeText(this, "Enter a valid 6-character code.", Toast.LENGTH_SHORT).show();
				return;
			}

			User user = UserSession.getInstance().getCurrentUser();
			if (user == null) {
				Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show();
				return;
			}

			if (user.getClubId() != null) {
				Toast.makeText(
						this,
						"You're already in a club. Leave first.",
						Toast.LENGTH_SHORT
				).show();
				return;
			}

			btnJoin.setEnabled(false);
			btnJoin.setText("Joining…");

			user.joinClubWithCode(
					code, success -> runOnUiThread(() -> {
						btnJoin.setEnabled(true);
						btnJoin.setText("Join Club");
						if (success) {
							new ProgressTracker().checkClubJoinBadge(user);
							Toast.makeText(
									this,
									"You've joined the club!",
									Toast.LENGTH_LONG
							).show();
							finish();
						} else {
							Toast.makeText(
									this,
									"Invalid or expired code. Please check with your club leader.",
									Toast.LENGTH_LONG
							).show();
						}
					})
			);
		});
	}
}
