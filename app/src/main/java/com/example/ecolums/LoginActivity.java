package com.example.ecolums;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Entry-point Activity for user authentication.
 *
 * <p>Handles both sign-in (email + password via Firebase Auth) and first-time
 * registration. On successful login it loads the user's Firestore profile into
 * {@link UserSession}, then navigates to {@link MainActivity} (students/leaders)
 * or {@link AdminDashboardActivity} (admins).</p>
 * <p>
 * Outstanding issues: "Forgot password" reset flow is not yet implemented.
 */
public class LoginActivity extends AppCompatActivity {

	// ── Hardcoded admin credentials ───────────────────────────────────────────
	private static final String ADMIN_EMAIL = "admin@admin.com";
	private static final String ADMIN_PASSWORD = "admin";
	// ─────────────────────────────────────────────────────────────────────────

	private static final int COLOR_SIGN_IN = 0xFF00BCD4; // teal_primary
	private static final int COLOR_SIGN_UP = 0xFF00897B; // teal_dark-ish green

	private FirebaseAuth auth;
	private FirebaseFirestore db;

	private TextInputEditText etEmail, etPassword;
	private TextInputLayout tilEmail, tilPassword;
	private MaterialButton btnContinue;
	private TextView tvHeaderSubtitle, tvModeDesc;
	private TextView tabSignIn, tabSignUp;
	private LinearLayout flAuthHeader;
	private ProgressBar progressBar;

	private View rootView;
	private boolean isSignUp = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);

		auth = FirebaseAuth.getInstance();
		db = FirebaseFirestore.getInstance();

		// Bind all views first — prevents NPE if auto-login callback fires setLoading()
		etEmail = findViewById(R.id.et_email);
		etPassword = findViewById(R.id.et_password);
		tilEmail = findViewById(R.id.til_email);
		tilPassword = findViewById(R.id.til_password);
		btnContinue = findViewById(R.id.btn_continue);
		progressBar = findViewById(R.id.progress_bar);
		tvHeaderSubtitle = findViewById(R.id.tv_header_subtitle);
		tvModeDesc = findViewById(R.id.tv_mode_description);
		flAuthHeader = findViewById(R.id.fl_auth_header);
		rootView = findViewById(R.id.root_login);
		tabSignIn = findViewById(R.id.tab_sign_in);
		tabSignUp = findViewById(R.id.tab_sign_up);

		// Set up the form UI and listeners always — needed whether auto-login
		// succeeds, fails, or is skipped entirely.
		applyModeUi(false);

		tabSignIn.setOnClickListener(v -> {
			if (isSignUp) switchMode(false);
		});
		tabSignUp.setOnClickListener(v -> {
			if (!isSignUp) switchMode(true);
		});
		btnContinue.setOnClickListener(v -> handleAuth());

		// Restore existing Firebase session.
		// Ignore anonymous sessions left over from the hardcoded admin login —
		// signing in anonymously persists the session across app restarts, so
		// we sign out here to return to the normal email/password form.
		FirebaseUser existing = auth.getCurrentUser();
		if (existing != null) {
			if (existing.isAnonymous()) {
				auth.signOut(); // clear stale anonymous session
			} else {
				setLoading(true);
				loadUserAndProceed(existing.getUid());
			}
		}
	}

	// ── UI helpers ────────────────────────────────────────────────────────────

	private void switchMode(boolean toSignUp) {
		isSignUp = toSignUp;
		applyModeUi(true);
	}

	private void applyModeUi(boolean animate) {
		int targetColor = isSignUp ? COLOR_SIGN_UP : COLOR_SIGN_IN;

		if (animate) {
			// Animate header background colour
			int currentColor = isSignUp ? COLOR_SIGN_IN : COLOR_SIGN_UP;
			ValueAnimator colorAnim = ValueAnimator.ofObject(
					new ArgbEvaluator(), currentColor, targetColor);
			colorAnim.setDuration(300);
			colorAnim.addUpdateListener(a -> {
				int c = (int) a.getAnimatedValue();
				flAuthHeader.setBackgroundColor(c);
				rootView.setBackgroundColor(c);
			});
			colorAnim.start();

			// Fade subtitle
			tvHeaderSubtitle.animate().alpha(0f).setDuration(150).withEndAction(() -> {
				tvHeaderSubtitle.setText(isSignUp
						? "Join the EcoLUMS community" : "Welcome back!");
				tvHeaderSubtitle.animate().alpha(1f).setDuration(150).start();
			}).start();

			// Fade description
			tvModeDesc.animate().alpha(0f).setDuration(120).withEndAction(() -> {
				tvModeDesc.setText(isSignUp
						? "Create your account to start tracking your impact"
						: "Enter your credentials to continue");
				tvModeDesc.animate().alpha(1f).setDuration(120).start();
			}).start();

			// Fade button
			btnContinue.animate().alpha(0f).setDuration(100).withEndAction(() -> {
				btnContinue.setText(isSignUp ? "Create Account" : "Sign In");
				btnContinue.animate().alpha(1f).setDuration(100).start();
			}).start();

		} else {
			flAuthHeader.setBackgroundColor(targetColor);
			rootView.setBackgroundColor(targetColor);
			tvHeaderSubtitle.setText(isSignUp
					? "Join the EcoLUMS community" : "Welcome back!");
			tvModeDesc.setText(isSignUp
					? "Create your account to start tracking your impact"
					: "Enter your credentials to continue");
			btnContinue.setText(isSignUp ? "Create Account" : "Sign In");
		}

		// Tab visual state
		if (isSignUp) {
			tabSignIn.setBackgroundResource(android.R.color.transparent);
			tabSignIn.setTextColor(getColor(R.color.text_secondary));
			tabSignIn.setTypeface(null, Typeface.NORMAL);
			tabSignUp.setBackgroundResource(R.drawable.bg_tab_selected);
			tabSignUp.setTextColor(getColor(R.color.white));
			tabSignUp.setTypeface(null, Typeface.BOLD);
		} else {
			tabSignIn.setBackgroundResource(R.drawable.bg_tab_selected);
			tabSignIn.setTextColor(getColor(R.color.white));
			tabSignIn.setTypeface(null, Typeface.BOLD);
			tabSignUp.setBackgroundResource(android.R.color.transparent);
			tabSignUp.setTextColor(getColor(R.color.text_secondary));
			tabSignUp.setTypeface(null, Typeface.NORMAL);
		}

		// Keep input field stroke/hint color in sync with the header colour
		if (tilEmail != null && tilPassword != null) {
			ColorStateList fieldColor = ColorStateList.valueOf(targetColor);
			tilEmail.setBoxStrokeColorStateList(fieldColor);
			tilEmail.setHintTextColor(fieldColor);
			tilPassword.setBoxStrokeColorStateList(fieldColor);
			tilPassword.setHintTextColor(fieldColor);
		}
	}

	private void setLoading(boolean loading) {
		if (progressBar != null) progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
		if (btnContinue != null) btnContinue.setEnabled(!loading);
	}

	// ── Auth entry point ──────────────────────────────────────────────────────

	private void handleAuth() {
		String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
		String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

		if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
			Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
			return;
		}

		// ── Hardcoded admin bypass ────────────────────────────────────────────
		if (ADMIN_EMAIL.equalsIgnoreCase(email)) {
			if (ADMIN_PASSWORD.equals(password)) {
				loginAsHardcodedAdmin();
			} else {
				Toast.makeText(this, "Incorrect admin password.", Toast.LENGTH_SHORT).show();
			}
			return;
		}
		// ─────────────────────────────────────────────────────────────────────

		if (password.length() < 6) {
			Toast.makeText(
					this,
					"Password must be at least 6 characters.",
					Toast.LENGTH_SHORT
			).show();
			return;
		}

		setLoading(true);
		if (isSignUp) {
			registerNewUser(email, password);
		} else {
			signInExistingUser(email, password);
		}
	}

	// ── Hardcoded admin ───────────────────────────────────────────────────────

	private void loginAsHardcodedAdmin() {
		setLoading(true);
		// Sign in anonymously so Firestore security rules see a valid auth token.
		// Requires Anonymous Authentication enabled in Firebase Console.
		auth.signInAnonymously()
				.addOnCompleteListener(task -> {
					// Whether anonymous auth succeeds or not, grant admin session
					User adminUser = new User(
							"admin_hardcoded",
							"Admin",
							ADMIN_EMAIL,
							User.ROLE_ADMIN
					);
					UserSession.getInstance().setCurrentUser(adminUser);
					setLoading(false);
					launchMain();
				});
	}

	// ── Firebase: registration ────────────────────────────────────────────────

	private void registerNewUser(String email, String password) {
		auth.createUserWithEmailAndPassword(email, password)
				.addOnSuccessListener(result -> {
					String uid = result.getUser().getUid();
					createUserDocument(uid, email);
				})
				.addOnFailureListener(e -> {
					setLoading(false);
					String msg = e.getMessage() != null ? e.getMessage() : "Registration failed.";
					if (msg.contains("already in use")) {
						Toast.makeText(
								this,
								"An account with this email already exists. Please sign in.",
								Toast.LENGTH_LONG
						).show();
						switchMode(false);
					} else {
						Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
					}
				});
	}

	private void createUserDocument(String uid, String email) {
		String displayName = email.split("@")[0];

		Map<String, Object> userData = new HashMap<>();
		userData.put("userId", uid);
		userData.put("name", displayName);
		userData.put("email", email);
		userData.put("role", User.ROLE_STUDENT);
		userData.put("totalPoints", 0.0);
		userData.put("earnedBadgeIds", new ArrayList<>());
		userData.put("shareDataWithClubs", false);
		userData.put("createdAt", com.google.firebase.Timestamp.now());

		db.collection("users").document(uid)
				.set(userData)
				.addOnSuccessListener(aVoid -> {
					FirebaseSeeder.seedIfNeeded();
					loadUserAndProceed(uid);
				})
				.addOnFailureListener(e -> {
					setLoading(false);
					Toast.makeText(
							this,
							"Account created but profile setup failed: " + e.getMessage(),
							Toast.LENGTH_LONG
					).show();
				});
	}

	// ── Firebase: sign-in ─────────────────────────────────────────────────────

	private void signInExistingUser(String email, String password) {
		auth.signInWithEmailAndPassword(email, password)
				.addOnSuccessListener(result -> {
					String uid = result.getUser().getUid();
					loadUserAndProceed(uid);
				})
				.addOnFailureListener(e -> {
					setLoading(false);
					String msg = e.getMessage() != null ? e.getMessage() : "";
					if (msg.contains("no user record") || msg.contains("user-not-found")
							|| msg.contains("INVALID_LOGIN_CREDENTIALS")) {
						Toast.makeText(
								this,
								"No account found. Please sign up first.",
								Toast.LENGTH_LONG
						).show();
						switchMode(true);
					} else {
						Toast.makeText(this, "Sign in failed: " + msg, Toast.LENGTH_LONG).show();
					}
				});
	}

	// ── Load user → session → main ────────────────────────────────────────────

	private void loadUserAndProceed(String uid) {
		db.collection("users").document(uid)
				.get()
				.addOnSuccessListener(doc -> {
					if (doc.exists()) {
						User user = doc.toObject(User.class);
						if (user != null) {
							UserSession.getInstance().setCurrentUser(user);
							setLoading(false);
							launchMain();
							return;
						}
					}
					// Document missing or failed to deserialize — create it so
					// UserSession is never null when MainActivity is opened.
					FirebaseUser firebaseUser = auth.getCurrentUser();
					String email = firebaseUser != null ? firebaseUser.getEmail() : null;
					if (email != null && !email.isEmpty()) {
						createUserDocument(uid, email);
					} else {
						// No email means the persisted session is anonymous or orphaned.
						// Sign out so the next app launch shows the login form directly.
						auth.signOut();
						setLoading(false);
						Toast.makeText(
								this, "Could not load your profile. Please sign in again.",
								Toast.LENGTH_LONG
						).show();
					}
				})
				.addOnFailureListener(e -> {
					FirebaseUser firebaseUser = auth.getCurrentUser();
					String email = firebaseUser != null ? firebaseUser.getEmail() : null;
					if (email != null && !email.isEmpty()) {
						createUserDocument(uid, email);
					} else {
						auth.signOut();
						setLoading(false);
						Toast.makeText(
								this, "Failed to load profile. Please sign in again.",
								Toast.LENGTH_LONG
						).show();
					}
				});
	}

	private void launchMain() {
		Intent intent = new Intent(this, MainActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(intent);
		finish();
	}
}
