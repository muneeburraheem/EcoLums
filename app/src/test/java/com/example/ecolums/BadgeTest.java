package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Unit tests for Badge — covers US_06.01 (Badges & Achievement Milestones).
 * <p>
 * Badge is a pure-Java model with no Firebase dependencies.
 */
public class BadgeTest {

	// -------------------------------------------------------------------------
	// US_06.01 — Badge creation defaults
	// -------------------------------------------------------------------------

	/**
	 * AC: New badge has status UNEARNED by default.
	 */
	@Test
	public void constructor_setsDefaultStatusToUnearned() {
		assertEquals(Badge.STATUS_UNEARNED, makeBadge(100).getStatus());
	}

	/**
	 * AC: New badge has dateEarned = null (not yet awarded).
	 */
	@Test
	public void constructor_setsDateEarnedToNull() {
		assertNull(makeBadge(100).getDateEarned());
	}

	/**
	 * AC: Constructor persists all provided fields.
	 */
	@Test
	public void constructor_setsAllProvidedFields() {
		Badge b = makeBadge(500);
		assertEquals("badge-1", b.getBadgeId());
		assertEquals("Cycle Champion", b.getName());
		assertEquals("You cycled 100km!", b.getDescription());
		assertEquals("Log 100km of cycling", b.getCriteria());
		assertEquals("https://storage/badge1.png", b.getIconUrl());
		assertEquals(500, b.getPointThreshold());
	}

	// -------------------------------------------------------------------------
	// US_06.01 — Badge earning
	// -------------------------------------------------------------------------

	/**
	 * AC: Status can be changed to EARNED.
	 */
	@Test
	public void setStatus_toEarned_isReflected() {
		Badge b = makeBadge(100);
		b.setStatus(Badge.STATUS_EARNED);
		assertEquals(Badge.STATUS_EARNED, b.getStatus());
	}

	/**
	 * AC: Point threshold of 0 means the badge is action-triggered, not point-based.
	 */
	@Test
	public void pointThreshold_zero_forActionTriggeredBadge() {
		Badge b = makeBadge(0);
		assertEquals(0, b.getPointThreshold());
	}

	/**
	 * AC: Point threshold can be updated.
	 */
	@Test
	public void setPointThreshold_updatesValue() {
		Badge b = makeBadge(100);
		b.setPointThreshold(250);
		assertEquals(250, b.getPointThreshold());
	}

	/**
	 * AC: badgeId can be set and retrieved.
	 */
	@Test
	public void badgeId_setAndGet() {
		Badge b = makeBadge(100);
		b.setBadgeId("new-badge-id");
		assertEquals("new-badge-id", b.getBadgeId());
	}

	// -------------------------------------------------------------------------
	// Constants
	// -------------------------------------------------------------------------

	/**
	 * AC: Status constants match the strings stored in Firestore.
	 */
	@Test
	public void statusConstants_haveCorrectValues() {
		assertEquals("EARNED", Badge.STATUS_EARNED);
		assertEquals("UNEARNED", Badge.STATUS_UNEARNED);
	}

	// -------------------------------------------------------------------------
	// Helper
	// -------------------------------------------------------------------------

	private Badge makeBadge(int threshold) {
		return new Badge(
				"badge-1",
				"Cycle Champion",
				"You cycled 100km!",
				"Log 100km of cycling",
				"https://storage/badge1.png",
				threshold
		);
	}
}
