package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for BadgeDefinitions — covers US_06.01 (Badge & Achievement Milestones).
 * <p>
 * BadgeDefinitions is a pure-Java factory class with no Firebase or Android
 * dependencies, so these tests run entirely on the JVM.
 */
public class BadgeDefinitionsTest {

    private List<Badge> badges;

    @Before
    public void setUp() {
        badges = BadgeDefinitions.getHardcodedBadges();
    }

    // -------------------------------------------------------------------------
    // List invariants
    // -------------------------------------------------------------------------

    /**
     * AC: getHardcodedBadges() never returns null.
     */
    @Test
    public void getHardcodedBadges_returnsNonNullList() {
        assertNotNull(badges);
    }

    /**
     * AC: getHardcodedBadges() returns exactly 8 badges.
     */
    @Test
    public void getHardcodedBadges_returnsEightBadges() {
        assertEquals(8, badges.size());
    }

    /**
     * AC: All badge IDs in the list are unique.
     */
    @Test
    public void getHardcodedBadges_allBadgeIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (Badge b : badges) {
            assertTrue("Duplicate badge ID found: " + b.getBadgeId(), ids.add(b.getBadgeId()));
        }
    }

    /**
     * AC: Every badge in the list is non-null.
     */
    @Test
    public void getHardcodedBadges_noBadgeIsNull() {
        for (Badge b : badges) {
            assertNotNull(b);
        }
    }

    /**
     * AC: Every badge has isHardcoded() == true.
     */
    @Test
    public void allBadges_isHardcodedTrue() {
        for (Badge b : badges) {
            assertTrue("Badge " + b.getBadgeId() + " should be hardcoded", b.isHardcoded());
        }
    }

    /**
     * AC: Every badge has tier == BASIC.
     */
    @Test
    public void allBadges_areBasicTier() {
        for (Badge b : badges) {
            assertEquals(
                    "Badge " + b.getBadgeId() + " should be BASIC tier",
                    Badge.TIER_BASIC, b.getBadgeTier()
            );
        }
    }

    /**
     * AC: Every badge has a non-null name.
     */
    @Test
    public void allBadges_haveNonNullName() {
        for (Badge b : badges) {
            assertNotNull("Badge " + b.getBadgeId() + " has null name", b.getName());
        }
    }

    /**
     * AC: Every badge has a non-null description.
     */
    @Test
    public void allBadges_haveNonNullDescription() {
        for (Badge b : badges) {
            assertNotNull("Badge " + b.getBadgeId() + " has null description", b.getDescription());
        }
    }

    // -------------------------------------------------------------------------
    // Badge ID constants
    // -------------------------------------------------------------------------

    @Test
    public void constant_firstStep_hasCorrectValue() {
        assertEquals("first_step", BadgeDefinitions.ID_FIRST_STEP);
    }

    @Test
    public void constant_clubMember_hasCorrectValue() {
        assertEquals("club_member", BadgeDefinitions.ID_CLUB_MEMBER);
    }

    @Test
    public void constant_challengeJoiner_hasCorrectValue() {
        assertEquals("challenge_joiner", BadgeDefinitions.ID_CHALLENGE_JOINER);
    }

    @Test
    public void constant_weekWarrior_hasCorrectValue() {
        assertEquals("week_warrior", BadgeDefinitions.ID_WEEK_WARRIOR);
    }

    @Test
    public void constant_monthChamp_hasCorrectValue() {
        assertEquals("month_champ", BadgeDefinitions.ID_MONTH_CHAMP);
    }

    @Test
    public void constant_ecoStarter_hasCorrectValue() {
        assertEquals("eco_starter", BadgeDefinitions.ID_ECO_STARTER);
    }

    @Test
    public void constant_greenHundred_hasCorrectValue() {
        assertEquals("green_hundred", BadgeDefinitions.ID_GREEN_HUNDRED);
    }

    @Test
    public void constant_bikeFive_hasCorrectValue() {
        assertEquals("bike_five", BadgeDefinitions.ID_BIKE_FIVE);
    }

    // -------------------------------------------------------------------------
    // Individual badge properties
    // -------------------------------------------------------------------------

    /**
     * AC: "First Step" badge is TYPE_HARDCODED with zero point threshold.
     */
    @Test
    public void firstStepBadge_isHardcodedTypeWithZeroThreshold() {
        Badge b = findById(BadgeDefinitions.ID_FIRST_STEP);
        assertNotNull(b);
        assertEquals(Badge.TYPE_HARDCODED, b.getBadgeType());
        assertEquals(0, b.getPointThreshold());
    }

    /**
     * AC: "Club Member" badge is TYPE_HARDCODED.
     */
    @Test
    public void clubMemberBadge_isHardcodedType() {
        Badge b = findById(BadgeDefinitions.ID_CLUB_MEMBER);
        assertNotNull(b);
        assertEquals(Badge.TYPE_HARDCODED, b.getBadgeType());
    }

    /**
     * AC: "Challenge Accepted" badge is TYPE_HARDCODED.
     */
    @Test
    public void challengeJoinerBadge_isHardcodedType() {
        Badge b = findById(BadgeDefinitions.ID_CHALLENGE_JOINER);
        assertNotNull(b);
        assertEquals(Badge.TYPE_HARDCODED, b.getBadgeType());
    }

    /**
     * AC: "Week Warrior" badge is TYPE_STREAK with streakRequired == 7.
     */
    @Test
    public void weekWarriorBadge_isStreakType_requires7Days() {
        Badge b = findById(BadgeDefinitions.ID_WEEK_WARRIOR);
        assertNotNull(b);
        assertEquals(Badge.TYPE_STREAK, b.getBadgeType());
        assertEquals(7, b.getStreakRequired());
    }

    /**
     * AC: "Month Champion" badge is TYPE_STREAK with streakRequired == 30.
     */
    @Test
    public void monthChampBadge_isStreakType_requires30Days() {
        Badge b = findById(BadgeDefinitions.ID_MONTH_CHAMP);
        assertNotNull(b);
        assertEquals(Badge.TYPE_STREAK, b.getBadgeType());
        assertEquals(30, b.getStreakRequired());
    }

    /**
     * AC: "Eco Starter" badge is TYPE_POINT_THRESHOLD with threshold == 50.
     */
    @Test
    public void ecoStarterBadge_hasPointThreshold50() {
        Badge b = findById(BadgeDefinitions.ID_ECO_STARTER);
        assertNotNull(b);
        assertEquals(Badge.TYPE_POINT_THRESHOLD, b.getBadgeType());
        assertEquals(50, b.getPointThreshold());
    }

    /**
     * AC: "Green Century" badge is TYPE_POINT_THRESHOLD with threshold == 100.
     */
    @Test
    public void greenHundredBadge_hasPointThreshold100() {
        Badge b = findById(BadgeDefinitions.ID_GREEN_HUNDRED);
        assertNotNull(b);
        assertEquals(Badge.TYPE_POINT_THRESHOLD, b.getBadgeType());
        assertEquals(100, b.getPointThreshold());
    }

    /**
     * AC: "Pedal Power" badge is TYPE_ACTIVITY_COUNT with activityCountRequired == 5
     * and activityType == "BIKE".
     */
    @Test
    public void bikeFiveBadge_isActivityCountType_requiresFiveBikeTrips() {
        Badge b = findById(BadgeDefinitions.ID_BIKE_FIVE);
        assertNotNull(b);
        assertEquals(Badge.TYPE_ACTIVITY_COUNT, b.getBadgeType());
        assertEquals(5, b.getActivityCountRequired());
        assertEquals(ActivityLog.TRANSPORT_BIKE, b.getActivityType());
    }

    /**
     * AC: getHardcodedBadges() called multiple times returns equal content
     * (factory method is idempotent).
     */
    @Test
    public void getHardcodedBadges_calledTwice_returnsSameContent() {
        List<Badge> first = BadgeDefinitions.getHardcodedBadges();
        List<Badge> second = BadgeDefinitions.getHardcodedBadges();
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getBadgeId(), second.get(i).getBadgeId());
        }
    }

    /**
     * AC: The list contains exactly one badge with each well-known ID.
     */
    @Test
    public void getHardcodedBadges_containsAllExpectedIds() {
        Set<String> ids = new HashSet<>();
        for (Badge b : badges) ids.add(b.getBadgeId());

        assertTrue(ids.contains(BadgeDefinitions.ID_FIRST_STEP));
        assertTrue(ids.contains(BadgeDefinitions.ID_CLUB_MEMBER));
        assertTrue(ids.contains(BadgeDefinitions.ID_CHALLENGE_JOINER));
        assertTrue(ids.contains(BadgeDefinitions.ID_WEEK_WARRIOR));
        assertTrue(ids.contains(BadgeDefinitions.ID_MONTH_CHAMP));
        assertTrue(ids.contains(BadgeDefinitions.ID_ECO_STARTER));
        assertTrue(ids.contains(BadgeDefinitions.ID_GREEN_HUNDRED));
        assertTrue(ids.contains(BadgeDefinitions.ID_BIKE_FIVE));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Badge findById(String id) {
        for (Badge b : badges) {
            if (id.equals(b.getBadgeId())) return b;
        }
        return null;
    }
}
