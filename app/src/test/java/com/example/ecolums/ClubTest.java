package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/**
 * Unit tests for Club — covers US_03.02 (Club Membership / Club creation).
 * <p>
 * Club is a pure-Java model with no Firebase dependencies.
 */
public class ClubTest {

	// -------------------------------------------------------------------------
	// US_03.02 — Club creation defaults
	// -------------------------------------------------------------------------

	/**
	 * AC: New club has joinCodeActive = false by default.
	 */
	@Test
	public void constructor_setsJoinCodeInactiveByDefault() {
		assertFalse(makeClub().isJoinCodeActive());
	}

	/**
	 * AC: New club has totalPoints = 0.0 by default.
	 */
	@Test
	public void constructor_setsTotalPointsToZero() {
		assertEquals(0.0, makeClub().getTotalPoints(), 1e-9);
	}

	/**
	 * AC: Constructor persists all provided fields.
	 */
	@Test
	public void constructor_setsAllProvidedFields() {
		Club club = makeClub();
		assertEquals("club-1", club.getClubId());
		assertEquals("Eco Warriors", club.getName());
		assertEquals("Saving the planet", club.getDescription());
		assertEquals("leader-uid", club.getLeaderId());
	}

	// -------------------------------------------------------------------------
	// US_03.02 — Join-code activation (club leader feature)
	// -------------------------------------------------------------------------

	/**
	 * AC: joinCodeActive can be set to true.
	 */
	@Test
	public void setJoinCodeActive_toTrue_isReflected() {
		Club club = makeClub();
		club.setJoinCodeActive(true);
		assertTrue(club.isJoinCodeActive());
	}

	/**
	 * AC: Join code string can be set and retrieved.
	 */
	@Test
	public void joinCode_setAndGet() {
		Club club = makeClub();
		club.setJoinCode("XYZ789");
		assertEquals("XYZ789", club.getJoinCode());
	}

	/**
	 * AC: Invite link can be set and retrieved.
	 */
	@Test
	public void inviteLink_setAndGet() {
		Club club = makeClub();
		club.setInviteLink("https://ecolums.app/join/XYZ789");
		assertEquals("https://ecolums.app/join/XYZ789", club.getInviteLink());
	}

	// -------------------------------------------------------------------------
	// US_03.02 — Member management
	// -------------------------------------------------------------------------

	/**
	 * AC: Member list can be set and retrieved.
	 */
	@Test
	public void memberIds_setAndGet() {
		Club club = makeClub();
		club.setMemberIds(Arrays.asList("uid1", "uid2", "uid3"));
		assertEquals(3, club.getMemberIds().size());
		assertTrue(club.getMemberIds().contains("uid1"));
	}

	/**
	 * AC: totalPoints can be updated.
	 */
	@Test
	public void setTotalPoints_updatesValue() {
		Club club = makeClub();
		club.setTotalPoints(250.5);
		assertEquals(250.5, club.getTotalPoints(), 1e-9);
	}

	/**
	 * AC: activeChallengeIds can be set and retrieved.
	 */
	@Test
	public void activeChallengeIds_setAndGet() {
		Club club = makeClub();
		club.setActiveChallengeIds(Arrays.asList("c1", "c2"));
		assertEquals(2, club.getActiveChallengeIds().size());
	}

	// -------------------------------------------------------------------------
	// Helper
	// -------------------------------------------------------------------------

	private Club makeClub() {
		return new Club("club-1", "Eco Warriors", "Saving the planet", "leader-uid");
	}
}
