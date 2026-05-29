package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.Arrays;

/**
 * Extended unit tests for Club — covers joinCodeExpiry, avatarUrl, no-arg
 * constructor, totalPoints arithmetic, and fields not covered in ClubTest.
 * <p>
 * Club is a pure-Java model with no Firebase dependencies.
 */
public class ClubExtendedTest {

    // -------------------------------------------------------------------------
    // No-arg constructor
    // -------------------------------------------------------------------------

    /**
     * AC: No-arg constructor does not throw; all fields are null/0/false.
     */
    @Test
    public void noArgConstructor_allFieldsAreDefault() {
        Club c = new Club();
        assertNull(c.getClubId());
        assertNull(c.getName());
        assertNull(c.getDescription());
        assertNull(c.getLeaderId());
        assertNull(c.getMemberIds());
        assertNull(c.getJoinCode());
        assertNull(c.getInviteLink());
        assertNull(c.getJoinCodeExpiry());
        assertNull(c.getAvatarUrl());
        assertFalse(c.isJoinCodeActive());
        assertEquals(0.0, c.getTotalPoints(), 1e-9);
        assertNull(c.getActiveChallengeIds());
    }

    // -------------------------------------------------------------------------
    // joinCodeExpiry
    // -------------------------------------------------------------------------

    /**
     * AC: joinCodeExpiry is null by default after 4-arg constructor.
     */
    @Test
    public void joinCodeExpiry_defaultNull() {
        assertNull(makeClub().getJoinCodeExpiry());
    }

    /**
     * AC: joinCodeExpiry can be set and retrieved.
     */
    @Test
    public void joinCodeExpiry_setAndGet() {
        Club c = makeClub();
        Timestamp expiry = new Timestamp(1_700_000_000L, 0);
        c.setJoinCodeExpiry(expiry);
        assertEquals(expiry, c.getJoinCodeExpiry());
    }

    /**
     * AC: joinCodeExpiry can be set to a future timestamp.
     */
    @Test
    public void joinCodeExpiry_futureTimestamp_setAndGet() {
        Club c = makeClub();
        // Far future (year ~2100 in epoch seconds)
        Timestamp future = new Timestamp(4_000_000_000L, 0);
        c.setJoinCodeExpiry(future);
        assertEquals(future, c.getJoinCodeExpiry());
    }

    /**
     * AC: An expired joinCodeExpiry (past) is before the current time.
     */
    @Test
    public void joinCodeExpiry_pastTimestamp_isBeforeNow() {
        Club c = makeClub();
        Timestamp past = new Timestamp(1_000_000_000L, 0); // year 2001
        c.setJoinCodeExpiry(past);
        assertTrue(past.toDate().before(new java.util.Date()));
    }

    // -------------------------------------------------------------------------
    // avatarUrl
    // -------------------------------------------------------------------------

    /**
     * AC: avatarUrl is null by default after 4-arg constructor.
     */
    @Test
    public void avatarUrl_defaultNull() {
        assertNull(makeClub().getAvatarUrl());
    }

    /**
     * AC: avatarUrl can be set and retrieved.
     */
    @Test
    public void avatarUrl_setAndGet() {
        Club c = makeClub();
        c.setAvatarUrl("https://storage.googleapis.com/clubs/eco-warriors.jpg");
        assertEquals("https://storage.googleapis.com/clubs/eco-warriors.jpg", c.getAvatarUrl());
    }

    /**
     * AC: avatarUrl can be set to null after being assigned.
     */
    @Test
    public void avatarUrl_setToNull_afterAssignment() {
        Club c = makeClub();
        c.setAvatarUrl("https://example.com/img.jpg");
        c.setAvatarUrl(null);
        assertNull(c.getAvatarUrl());
    }

    // -------------------------------------------------------------------------
    // Points arithmetic
    // -------------------------------------------------------------------------

    /**
     * AC: totalPoints can be accumulated manually (simulates ProgressTracker updates).
     */
    @Test
    public void totalPoints_canBeAccumulated() {
        Club c = makeClub();
        c.setTotalPoints(c.getTotalPoints() + 100.0);
        c.setTotalPoints(c.getTotalPoints() + 50.0);
        assertEquals(150.0, c.getTotalPoints(), 1e-9);
    }

    /**
     * AC: totalPoints can be set to a fractional value.
     */
    @Test
    public void totalPoints_fractionalValue_setAndGet() {
        Club c = makeClub();
        c.setTotalPoints(123.456);
        assertEquals(123.456, c.getTotalPoints(), 1e-9);
    }

    // -------------------------------------------------------------------------
    // activeChallengeIds
    // -------------------------------------------------------------------------

    /**
     * AC: activeChallengeIds is null by default.
     */
    @Test
    public void activeChallengeIds_defaultNull() {
        assertNull(makeClub().getActiveChallengeIds());
    }

    /**
     * AC: activeChallengeIds can hold multiple challenge IDs.
     */
    @Test
    public void activeChallengeIds_multipleIds_setAndGet() {
        Club c = makeClub();
        c.setActiveChallengeIds(Arrays.asList("c1", "c2", "c3"));
        assertEquals(3, c.getActiveChallengeIds().size());
        assertTrue(c.getActiveChallengeIds().contains("c2"));
    }

    // -------------------------------------------------------------------------
    // Join code lifecycle
    // -------------------------------------------------------------------------

    /**
     * AC: A club begins with no join code (null).
     */
    @Test
    public void joinCode_defaultNull() {
        assertNull(makeClub().getJoinCode());
    }

    /**
     * AC: Join code activation and deactivation toggles work.
     */
    @Test
    public void joinCodeActive_canBeToggledOnAndOff() {
        Club c = makeClub();
        c.setJoinCodeActive(true);
        assertTrue(c.isJoinCodeActive());
        c.setJoinCodeActive(false);
        assertFalse(c.isJoinCodeActive());
    }

    /**
     * AC: A complete join-code setup (code + expiry + active flag) is consistent.
     */
    @Test
    public void joinCode_fullSetup_allFieldsConsistent() {
        Club c = makeClub();
        c.setJoinCode("ABCDE1");
        c.setJoinCodeActive(true);
        c.setJoinCodeExpiry(new Timestamp(4_000_000_000L, 0)); // future

        assertEquals("ABCDE1", c.getJoinCode());
        assertTrue(c.isJoinCodeActive());
        assertFalse(c.getJoinCodeExpiry().toDate().before(new java.util.Date()));
    }

    // -------------------------------------------------------------------------
    // Setters for all 4-arg constructor fields
    // -------------------------------------------------------------------------

    /**
     * AC: All 4-arg constructor fields can be updated via setters.
     */
    @Test
    public void fourArgConstructorFields_updatableViaSetters() {
        Club c = makeClub();
        c.setClubId("club-new");
        c.setName("New Name");
        c.setDescription("New Description");
        c.setLeaderId("new-leader");

        assertEquals("club-new", c.getClubId());
        assertEquals("New Name", c.getName());
        assertEquals("New Description", c.getDescription());
        assertEquals("new-leader", c.getLeaderId());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Club makeClub() {
        return new Club("club-1", "Eco Warriors", "Saving the planet", "leader-uid");
    }
}
