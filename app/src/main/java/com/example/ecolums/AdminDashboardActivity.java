package com.example.ecolums;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * Entry-point Activity for the admin section of the app.
 *
 * <p>Validates that the current session belongs to an ADMIN user before inflating
 * the layout. Hosts a {@link ViewPager2} backed by {@link AdminPagerAdapter} with
 * five tabs: Users, Privacy, Audit Log, Tips, and Metrics. Redirects non-admins
 * to {@link MainActivity}.</p>
 * <p>
 * Outstanding issues: None at this time.
 */
public class AdminDashboardActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (!UserSession.getInstance().isAdmin()) {
			Toast.makeText(this, getString(R.string.access_denied), Toast.LENGTH_LONG).show();
			startActivity(new Intent(this, MainActivity.class));
			finish();
			return;
		}

		setContentView(R.layout.activity_admin_dashboard);

		// Toolbar with back navigation
		Toolbar toolbar = findViewById(R.id.toolbar_admin);
		setSupportActionBar(toolbar);
		if (getSupportActionBar() != null) {
			getSupportActionBar().setTitle("Admin Dashboard");
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}
		toolbar.setNavigationOnClickListener(v -> finish());

		// Show admin name in identity card
		User admin = UserSession.getInstance().getCurrentUser();
		TextView tvAdminName = findViewById(R.id.tv_admin_name);
		if (tvAdminName != null && admin != null) {
			tvAdminName.setText(admin.getName());
		}

		ViewPager2 viewPager = findViewById(R.id.view_pager);
		TabLayout tabLayout = findViewById(R.id.tab_layout);

		AdminPagerAdapter pagerAdapter = new AdminPagerAdapter(this);
		viewPager.setAdapter(pagerAdapter);

		new TabLayoutMediator(
				tabLayout, viewPager, (tab, position) -> {
			switch (position) {
			case 0:
				tab.setText("📈 Impact");
				break;
			case 1:
				tab.setText("👥 Users");
				break;
			case 2:
				tab.setText("🔒 Privacy");
				break;
			case 3:
				tab.setText("📋 Audit");
				break;
			case 4:
				tab.setText("💡 Tips");
				break;
			case 5:
				tab.setText("⚙ Metrics");
				break;
			case 6:
				tab.setText("🏅 Badges");
				break;
			case 7:
				tab.setText("🛍️ Merch");
				break;
			case 8:
				tab.setText("🔧 Health");
				break;
			case 9:
				tab.setText("🚨 Anomalies");
				break;
			}
		}
		).attach();
	}
}
