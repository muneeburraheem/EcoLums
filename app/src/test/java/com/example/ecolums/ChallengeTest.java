package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for Challenge — covers US_03.01 (Green Challenges).
 * <p>
 * Challenge is a pure-Java model with no Firebase dependencies, so these
 * tests run entirely on the JVM without any mocking.
 */
public class ChallengeTest {

	// -------------------------------------------------------------------------
	// US_03.01 — Challenge creation defaults
	// -------------------------------------------------------------------------

	/**
	 * AC: New challenge starts with status UPCOMING.
	 */
	@Test
	public void constructor_setsDefaultStatusToUpcoming() {
		Challenge c = makeChallenge();
		assertEquals(Challenge.STATUS_UPCOMING, c.getStatus());
	}

	/**
	 * AC: Constructor persists all provided fields.
	 */
	@Test
	public void constructor_setsAllProvidedFields() {
		Challenge c = makeChallenge();
		assertEquals("challenge-1", c.getChallengeId());
		assertEquals("Bike Week", c.getTitle());
		assertEquals("Ride your bike all week", c.getDescription());
		assertEquals("creator-uid", c.getCreatorId());
		assertEquals(Challenge.GOAL_TYPE_TRANSPORT, c.getGoalType());
		assertEquals(100.0, c.getTargetValue(), 1e-9);
		assertEquals("km", c.getTargetUnit());
	}

	/**
	 * AC: isManualDeactivated defaults to false.
	 */
	@Test
	public void isManualDeactivated_defaultFalse() {
		assertFalse(makeChallenge().isManualDeactivated());
	}

	/**
	 * AC: notificationMilestones are stored from the constructor.
	 */
	@Test
	public void constructor_storesNotificationMilestones() {
		List<Integer> milestones = Arrays.asList(50, 100);
		Challenge c = new Challenge(
				"cid", "t", "d", "uid",
				Challenge.GOAL_TYPE_OVERALL, 50.0, "pts",
				null, null, milestones
		);
		assertEquals(milestones, c.getNotificationMilestones());
	}

	// -------------------------------------------------------------------------
	// US_03.01 — Status transitions
	// -------------------------------------------------------------------------

	/**
	 * AC: Status can be changed to ACTIVE.
	 */
	@Test
	public void setStatus_toActive_isReflected() {
		Challenge c = makeChallenge();
		c.setStatus(Challenge.STATUS_ACTIVE);
		assertEquals(Challenge.STATUS_ACTIVE, c.getStatus());
	}

	/**
	 * AC: Status can be changed to COMPLETED.
	 */
	@Test
	public void setStatus_toCompleted_isReflected() {
		Challenge c = makeChallenge();
		c.setStatus(Challenge.STATUS_COMPLETED);
		assertEquals(Challenge.STATUS_COMPLETED, c.getStatus());
	}

	// -------------------------------------------------------------------------
	// US_03.03 — Invite code management
	// -------------------------------------------------------------------------

	/**
	 * AC: Invite code can be set and retrieved.
	 */
	@Test
	public void inviteCode_setAndGet() {
		Challenge c = makeChallenge();
		c.setInviteCode("ABC123");
		assertEquals("ABC123", c.getInviteCode());
	}

	/**
	 * AC: Manual deactivation flag can be set to true.
	 */
	@Test
	public void setManualDeactivated_toTrue_isReflected() {
		Challenge c = makeChallenge();
		c.setManualDeactivated(true);
		assertTrue(c.isManualDeactivated());
	}

	// -------------------------------------------------------------------------
	// Constants
	// -------------------------------------------------------------------------

	/**
	 * AC: Status constants match Firestore-stored string values.
	 */
	@Test
	public void statusConstants_haveCorrectValues() {
		assertEquals("UPCOMING", Challenge.STATUS_UPCOMING);
		assertEquals("ACTIVE", Challenge.STATUS_ACTIVE);
		assertEquals("COMPLETED", Challenge.STATUS_COMPLETED);
	}

	/**
	 * AC: Goal type constants match ActivityLog category values.
	 */
	@Test
	public void goalTypeConstants_haveCorrectValues() {
		assertEquals("TRANSPORT", Challenge.GOAL_TYPE_TRANSPORT);
		assertEquals("ENERGY", Challenge.GOAL_TYPE_ENERGY);
		assertEquals("WASTE", Challenge.GOAL_TYPE_WASTE);
		assertEquals("OVERALL", Challenge.GOAL_TYPE_OVERALL);
	}

	// -------------------------------------------------------------------------
	// Helper
	// -------------------------------------------------------------------------

	private Challenge makeChallenge() {
		return new Challenge(
				"challenge-1",
				"Bike Week",
				"Ride your bike all week",
				"creator-uid",
				Challenge.GOAL_TYPE_TRANSPORT,
				100.0, "km",
				null, null,
				Arrays.asList(50, 100)
		);
	}
}
