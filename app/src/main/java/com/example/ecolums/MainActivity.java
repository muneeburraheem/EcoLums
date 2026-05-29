package com.example.ecolums;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Shell activity hosting the three bottom-nav fragments:
 * - HomeFragment   (nav_home)
 * - LogsFragment   (nav_logs)
 * - ChallengesFragment (nav_challenges)
 * <p>
 * Also shows the admin shield icon in the toolbar if the current user is an admin.
 */
public class MainActivity extends AppCompatActivity {

	private Toolbar toolbar;
	private BottomNavigationView bottomNav;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Auth guard: redirect to login if no active session.
		// UserSession covers both Firebase-backed users and the hardcoded admin.
		if (!UserSession.getInstance().isLoggedIn()) {
			Intent loginIntent = new Intent(this, LoginActivity.class);
			loginIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			startActivity(loginIntent);
			finish();
			return;
		}

		toolbar = findViewById(R.id.toolbar);
		bottomNav = findViewById(R.id.bottom_nav);

		setSupportActionBar(toolbar);

		// Start on Home
		if (savedInstanceState == null) {
			loadFragment(new HomeFragment());
		}

		bottomNav.setOnItemSelectedListener(item -> {
			int id = item.getItemId();
			if (id == R.id.nav_home) {
				loadFragment(new HomeFragment());
			} else if (id == R.id.nav_logs) {
				loadFragment(new LogsFragment());
			} else if (id == R.id.nav_impact) {
				loadFragment(new PersonalImpactFragment());
			} else if (id == R.id.nav_challenges) {
				loadFragment(new ChallengesFragment());
			} else if (id == R.id.nav_clubs) {
				loadFragment(new ClubsFragment());
			}
			return true;
		});

		// Default selection
		bottomNav.setSelectedItemId(R.id.nav_home);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main_menu, menu);
		// Show admin shield only for admin users
		MenuItem adminItem = menu.findItem(R.id.menu_admin);
		if (adminItem != null) {
			adminItem.setVisible(UserSession.getInstance().isAdmin());
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.menu_admin) {
			openAdminDashboard();
			return true;
		} else if (id == R.id.menu_sign_out) {
			signOut();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void openAdminDashboard() {
		// RBAC: double-check role before launching
		if (!UserSession.getInstance().isAdmin()) {
			android.widget.Toast.makeText(
					this,
					getString(R.string.access_denied), android.widget.Toast.LENGTH_SHORT
			).show();
			return;
		}
		startActivity(new Intent(this, AdminDashboardActivity.class));
	}

	private void signOut() {
		FirebaseAuth.getInstance().signOut();
		UserSession.getInstance().clear();
		Intent intent = new Intent(this, LoginActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(intent);
	}

	/**
	 * Switches bottom nav to the Challenges tab. Called from HomeFragment "View All".
	 */
	public void navigateToChallenges() {
		bottomNav.setSelectedItemId(R.id.nav_challenges);
	}

	private void loadFragment(Fragment fragment) {
		getSupportFragmentManager()
				.beginTransaction()
				.replace(R.id.fragment_container, fragment)
				.commit();
	}
}
