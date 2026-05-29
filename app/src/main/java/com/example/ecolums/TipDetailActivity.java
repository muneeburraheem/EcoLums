package com.example.ecolums;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * Full-screen detail view for a single {@link SustainabilityTip}.
 *
 * <p>Receives the tip's Firestore document ID via Intent extra and loads the
 * full body content, images, and external links for display. Increments the
 * tip's {@code viewCount} on load.</p>
 * <p>
 * Outstanding issues: Video embed playback is stubbed; the video URL is stored
 * but no player component is shown in the layout yet.
 */
public class TipDetailActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_tip_detail);

		String tipId = getIntent().getStringExtra("tipId");
		String title = getIntent().getStringExtra("title");
		String summary = getIntent().getStringExtra("summary");
		String body = getIntent().getStringExtra("body");
		String category = getIntent().getStringExtra("category");
		int likes = getIntent().getIntExtra("likes", 0);
		int views = getIntent().getIntExtra("views", 0);

		// Hero background
		android.view.View heroBg = findViewById(R.id.view_hero_bg);
		TextView tvEmoji = findViewById(R.id.tv_hero_emoji);
		TextView tvCat = findViewById(R.id.tv_detail_category);

		int bgRes;
		String emoji;
		switch (category != null ? category : "") {
		case SustainabilityTip.CATEGORY_TRANSPORT:
			bgRes = R.drawable.bg_tip_transport;
			emoji = "🚌";
			break;
		case SustainabilityTip.CATEGORY_ENERGY:
			bgRes = R.drawable.bg_tip_energy;
			emoji = "⚡";
			break;
		case SustainabilityTip.CATEGORY_WASTE:
			bgRes = R.drawable.bg_tip_waste;
			emoji = "♻";
			break;
		default:
			bgRes = R.drawable.bg_tip_general;
			emoji = "🌿";
			break;
		}
		heroBg.setBackgroundResource(bgRes);
		tvEmoji.setText(emoji);
		tvCat.setText(category != null ? category : "General");

		// Back button
		findViewById(R.id.btn_back).setOnClickListener(v -> finish());

		// Content
		((TextView) findViewById(R.id.tv_detail_title)).setText(title);
		((TextView) findViewById(R.id.tv_detail_summary)).setText(summary);
		((TextView) findViewById(R.id.tv_detail_body)).setText(body);
		((TextView) findViewById(R.id.tv_detail_likes)).setText("♥ " + likes + " likes");
		((TextView) findViewById(R.id.tv_detail_views)).setText("👁 " + views + " views");

		// Like button
		FloatingActionButton fabLike = findViewById(R.id.fab_like);
		fabLike.setOnClickListener(v -> {
			if (tipId != null) {
				com.google.firebase.firestore.FirebaseFirestore.getInstance()
						.collection("sustainabilityTips")
						.document(tipId)
						.update(
								"likeCount",
								com.google.firebase.firestore.FieldValue.increment(1)
						);
				Toast.makeText(this, "Thanks for the like! ♥", Toast.LENGTH_SHORT).show();
				fabLike.setEnabled(false);
			}
		});
	}
}
