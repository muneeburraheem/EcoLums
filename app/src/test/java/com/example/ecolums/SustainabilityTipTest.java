package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.Arrays;

/**
 * Unit tests for SustainabilityTip — covers US_07.01 (Sustainability Tips Gallery).
 * <p>
 * SustainabilityTip is a pure-Java model with no Firebase dependencies
 * (Timestamp is received as a parameter, not obtained via Firebase services).
 */
public class SustainabilityTipTest {

	// -------------------------------------------------------------------------
	// US_07.01 — Tip creation defaults
	// -------------------------------------------------------------------------

	/**
	 * AC: New tip starts with viewCount = 0.
	 */
	@Test
	public void constructor_setsDefaultViewCountToZero() {
		assertEquals(0, makeTip().getViewCount());
	}

	/**
	 * AC: New tip starts with likeCount = 0.
	 */
	@Test
	public void constructor_setsDefaultLikeCountToZero() {
		assertEquals(0, makeTip().getLikeCount());
	}

	/**
	 * AC: New tip has isNew = true so the "New" badge appears in the list view.
	 */
	@Test
	public void constructor_setsIsNewToTrue() {
		assertTrue(makeTip().isNew());
	}

	/**
	 * AC: Constructor persists all provided fields.
	 */
	@Test
	public void constructor_setsAllProvidedFields() {
		Timestamp ts = new Timestamp(1_700_000_000L, 0);
		SustainabilityTip tip = new SustainabilityTip(
				"tip-1",
				"Ride Your Bike",
				"Cycling reduces CO2 emissions",
				"Full body content here...",
				SustainabilityTip.CATEGORY_TRANSPORT,
				"admin-uid",
				ts
		);

		assertEquals("tip-1", tip.getTipId());
		assertEquals("Ride Your Bike", tip.getTitle());
		assertEquals("Cycling reduces CO2 emissions", tip.getSummary());
		assertEquals("Full body content here...", tip.getBodyContent());
		assertEquals(SustainabilityTip.CATEGORY_TRANSPORT, tip.getCategory());
		assertEquals("admin-uid", tip.getAuthorAdminId());
		assertEquals(ts, tip.getPublishedAt());
	}

	// -------------------------------------------------------------------------
	// US_07.01 — Engagement tracking
	// -------------------------------------------------------------------------

	/**
	 * AC: viewCount can be incremented (e.g., when a student opens the tip).
	 */
	@Test
	public void setViewCount_updatesCount() {
		SustainabilityTip tip = makeTip();
		tip.setViewCount(42);
		assertEquals(42, tip.getViewCount());
	}

	/**
	 * AC: likeCount can be updated.
	 */
	@Test
	public void setLikeCount_updatesCount() {
		SustainabilityTip tip = makeTip();
		tip.setLikeCount(15);
		assertEquals(15, tip.getLikeCount());
	}

	// -------------------------------------------------------------------------
	// US_07.01 — "New" badge logic
	// -------------------------------------------------------------------------

	/**
	 * AC: isNew flag can be set to false (e.g., after 7 days).
	 */
	@Test
	public void setNew_toFalse_isReflected() {
		SustainabilityTip tip = makeTip();
		tip.setNew(false);
		assertFalse(tip.isNew());
	}

	// -------------------------------------------------------------------------
	// US_07.01 — Category filtering
	// -------------------------------------------------------------------------

	/**
	 * AC: Category constants match the folder/filter labels in the UI.
	 */
	@Test
	public void categoryConstants_haveCorrectValues() {
		assertEquals("Transport", SustainabilityTip.CATEGORY_TRANSPORT);
		assertEquals("Energy", SustainabilityTip.CATEGORY_ENERGY);
		assertEquals("Waste", SustainabilityTip.CATEGORY_WASTE);
		assertEquals("General", SustainabilityTip.CATEGORY_GENERAL);
	}

	/**
	 * AC: A tip's category can be changed after creation.
	 */
	@Test
	public void setCategory_updatesCategory() {
		SustainabilityTip tip = makeTip();
		tip.setCategory(SustainabilityTip.CATEGORY_ENERGY);
		assertEquals(SustainabilityTip.CATEGORY_ENERGY, tip.getCategory());
	}

	// -------------------------------------------------------------------------
	// US_07.01 — Media / links
	// -------------------------------------------------------------------------

	/**
	 * AC: imageUrls list can be set and retrieved.
	 */
	@Test
	public void imageUrls_setAndGet() {
		SustainabilityTip tip = makeTip();
		tip.setImageUrls(Arrays.asList("https://img1.png", "https://img2.png"));
		assertEquals(2, tip.getImageUrls().size());
	}

	/**
	 * AC: videoUrl can be set and retrieved.
	 */
	@Test
	public void videoUrl_setAndGet() {
		SustainabilityTip tip = makeTip();
		tip.setVideoUrl("https://youtube.com/xyz");
		assertEquals("https://youtube.com/xyz", tip.getVideoUrl());
	}

	/**
	 * AC: lastEditedAt is null by default (tip has not been edited yet).
	 */
	@Test
	public void lastEditedAt_isNullByDefault() {
		assertNull(makeTip().getLastEditedAt());
	}

	// -------------------------------------------------------------------------
	// Helper
	// -------------------------------------------------------------------------

	private SustainabilityTip makeTip() {
		return new SustainabilityTip(
				"tip-1",
				"Ride Your Bike",
				"Cycling reduces CO2",
				"Full body...",
				SustainabilityTip.CATEGORY_TRANSPORT,
				"admin-uid",
				new Timestamp(1_700_000_000L, 0)
		);
	}
}
