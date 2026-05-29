package com.example.ecolums;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * ViewPager2 adapter for the admin dashboard tab strip.
 *
 * <p>Maps tab positions to the five admin fragments:
 * Users (0), Privacy (1), Audit Log (2), Tips (3), Metrics (4).
 * Hosted by {@link AdminDashboardActivity}.</p>
 * <p>
 * Outstanding issues: Tab titles are set via {@link com.google.android.material.tabs.TabLayoutMediator}
 * in the host activity using hard-coded strings rather than string resources.
 */
public class AdminPagerAdapter extends FragmentStateAdapter {

	public AdminPagerAdapter(@NonNull FragmentActivity fa) {
		super(fa);
	}

	@NonNull
	@Override
	public Fragment createFragment(int position) {
		switch (position) {
		case 0:
			return new AdminImpactFragment();
		case 1:
			return new AdminUsersFragment();
		case 2:
			return new AdminPrivacyFragment();
		case 3:
			return new AdminAuditFragment();
		case 4:
			return new AdminTipsFragment();
		case 5:
			return new AdminMetricsFragment();
		case 6:
			return new AdminBadgesFragment();
		case 7:
			return new AdminMerchFragment();
		case 8:
			return new AdminSystemHealthFragment();
		case 9:
			return new AdminAnomalyFragment();
		default:
			return new AdminImpactFragment();
		}
	}

	@Override
	public int getItemCount() {
		return 10;
	}
}
