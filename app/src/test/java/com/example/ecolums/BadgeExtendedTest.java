package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Extended unit tests for Badge — covers convenience methods, display-emoji
 * resolution, all type/tier constants, and every getter/setter not tested
 * by the primary BadgeTest suite.
 * <p>
 * No Firebase services are needed; Badge is a pure-Java model.
 */
public class BadgeExtendedTest {

    // -------------------------------------------------------------------------
    // isEarned() convenience method
    // -------------------------------------------------------------------------

    /**
     * AC: A newly created badge (status = UNEARNED) returns false from isEarned().
     */
    @Test
    public void isEarned_defaultStatus_returnsFalse() {
        assertFalse(makeBadge().isEarned());
    }

    /**
     * AC: After setting status to EARNED, isEarned() returns true.
     */
    @Test
    public void isEarned_afterSettingStatusEarned_returnsTrue() {
        Badge b = makeBadge();
        b.setStatus(Badge.STATUS_EARNED);
        assertTrue(b.isEarned());
    }

    /**
     * AC: After setting status back to UNEARNED, isEarned() returns false again.
     */
    @Test
    public void isEarned_afterResettingStatusToUnearned_returnsFalse() {
        Badge b = makeBadge();
        b.setStatus(Badge.STATUS_EARNED);
        b.setStatus(Badge.STATUS_UNEARNED);
        assertFalse(b.isEarned());
    }

    /**
     * AC: isEarned() returns false when status is an arbitrary non-EARNED string.
     */
    @Test
    public void isEarned_withArbitraryStatus_returnsFalse() {
        Badge b = makeBadge();
        b.setStatus("PENDING");
        assertFalse(b.isEarned());
    }

    // -------------------------------------------------------------------------
    // isPremium() convenience method
    // -------------------------------------------------------------------------

    /**
     * AC: Default constructor sets TIER_BASIC; isPremium() returns false.
     */
    @Test
    public void isPremium_basicTier_returnsFalse() {
        Badge b = makeBadge();
        assertEquals(Badge.TIER_BASIC, b.getBadgeTier());
        assertFalse(b.isPremium());
    }

    /**
     * AC: After setting tier to PREMIUM, isPremium() returns true.
     */
    @Test
    public void isPremium_premiumTier_returnsTrue() {
        Badge b = makeBadge();
        b.setBadgeTier(Badge.TIER_PREMIUM);
        assertTrue(b.isPremium());
    }

    /**
     * AC: After reverting to BASIC, isPremium() returns false.
     */
    @Test
    public void isPremium_afterRevertingToBasic_returnsFalse() {
        Badge b = makeBadge();
        b.setBadgeTier(Badge.TIER_PREMIUM);
        b.setBadgeTier(Badge.TIER_BASIC);
        assertFalse(b.isPremium());
    }

    // -------------------------------------------------------------------------
    // getDisplayEmoji() — emoji vs URL, type-based fallbacks
    // -------------------------------------------------------------------------

    /**
     * AC: If iconUrl is a single emoji (length ≤ 4), getDisplayEmoji returns it directly.
     */
    @Test
    public void getDisplayEmoji_shortIconUrl_returnsIconUrl() {
        Badge b = makeBadge();
        b.setIconUrl("🌱");
        assertEquals("🌱", b.getDisplayEmoji());
    }

    /**
     * AC: A 4-character iconUrl (e.g. a 2-codepoint emoji) is returned directly.
     */
    @Test
    public void getDisplayEmoji_fourCharIconUrl_returnsIconUrl() {
        Badge b = makeBadge();
        b.setIconUrl("💯"); // 2 codepoints = length 2 in Java
        assertEquals("💯", b.getDisplayEmoji());
    }

    /**
     * AC: If iconUrl is a full URL (length > 4), TYPE_STREAK → 🔥.
     */
    @Test
    public void getDisplayEmoji_streakTypeWithUrl_returnsFire() {
        Badge b = makeBadge();
        b.setIconUrl("https://storage.googleapis.com/badge.png");
        b.setBadgeType(Badge.TYPE_STREAK);
        assertEquals("🔥", b.getDisplayEmoji());
    }

    /**
     * AC: TYPE_ACTIVITY_COUNT → ⭐ when iconUrl is long.
     */
    @Test
    public void getDisplayEmoji_activityCountTypeWithUrl_returnsStar() {
        Badge b = makeBadge();
        b.setIconUrl("https://storage.googleapis.com/badge.png");
        b.setBadgeType(Badge.TYPE_ACTIVITY_COUNT);
        assertEquals("⭐", b.getDisplayEmoji());
    }

    /**
     * AC: TYPE_CHALLENGE_COMPLETION → 🏆 when iconUrl is long.
     */
    @Test
    public void getDisplayEmoji_challengeCompletionTypeWithUrl_returnsTrophy() {
        Badge b = makeBadge();
        b.setIconUrl("https://storage.googleapis.com/badge.png");
        b.setBadgeType(Badge.TYPE_CHALLENGE_COMPLETION);
        assertEquals("🏆", b.getDisplayEmoji());
    }

    /**
     * AC: TYPE_CHALLENGE_PERCENTAGE → 📊 when iconUrl is long.
     */
    @Test
    public void getDisplayEmoji_challengePercentageTypeWithUrl_returnsChart() {
        Badge b = makeBadge();
        b.setIconUrl("https://storage.googleapis.com/badge.png");
        b.setBadgeType(Badge.TYPE_CHALLENGE_PERCENTAGE);
        assertEquals("📊", b.getDisplayEmoji());
    }

    /**
     * AC: TYPE_POINT_THRESHOLD → 💚 when iconUrl is long.
     */
    @Test
    public void getDisplayEmoji_pointThresholdTypeWithUrl_returnsGreenHeart() {
        Badge b = makeBadge();
        b.setIconUrl("https://storage.googleapis.com/badge.png");
        b.setBadgeType(Badge.TYPE_POINT_THRESHOLD);
        assertEquals("💚", b.getDisplayEmoji());
    }

    /**
     * AC: Unknown type and long iconUrl → default fallback 🌿.
     */
    @Test
    public void getDisplayEmoji_unknownTypeWithUrl_returnsLeaf() {
        Badge b = makeBadge();
        b.setIconUrl("https://storage.googleapis.com/badge.png");
        b.setBadgeType("UNKNOWN_TYPE");
        assertEquals("🌿", b.getDisplayEmoji());
    }

    /**
     * AC: Null iconUrl also follows the type-based fallback path (null.length() guard).
     */
    @Test
    public void getDisplayEmoji_nullIconUrl_withStreakType_returnsFire() {
        Badge b = makeBadge();
        b.setIconUrl(null);
        b.setBadgeType(Badge.TYPE_STREAK);
        assertEquals("🔥", b.getDisplayEmoji());
    }

    // -------------------------------------------------------------------------
    // TYPE_* constants
    // -------------------------------------------------------------------------

    @Test
    public void typeConstant_pointThreshold_hasCorrectValue() {
        assertEquals("POINT_THRESHOLD", Badge.TYPE_POINT_THRESHOLD);
    }

    @Test
    public void typeConstant_streak_hasCorrectValue() {
        assertEquals("STREAK", Badge.TYPE_STREAK);
    }

    @Test
    public void typeConstant_activityCount_hasCorrectValue() {
        assertEquals("ACTIVITY_COUNT", Badge.TYPE_ACTIVITY_COUNT);
    }

    @Test
    public void typeConstant_challengeCompletion_hasCorrectValue() {
        assertEquals("CHALLENGE_COMPLETION", Badge.TYPE_CHALLENGE_COMPLETION);
    }

    @Test
    public void typeConstant_challengePercentage_hasCorrectValue() {
        assertEquals("CHALLENGE_PERCENTAGE", Badge.TYPE_CHALLENGE_PERCENTAGE);
    }

    @Test
    public void typeConstant_hardcoded_hasCorrectValue() {
        assertEquals("HARDCODED", Badge.TYPE_HARDCODED);
    }

    // -------------------------------------------------------------------------
    // TIER_* constants
    // -------------------------------------------------------------------------

    @Test
    public void tierConstant_basic_hasCorrectValue() {
        assertEquals("BASIC", Badge.TIER_BASIC);
    }

    @Test
    public void tierConstant_premium_hasCorrectValue() {
        assertEquals("PREMIUM", Badge.TIER_PREMIUM);
    }

    // -------------------------------------------------------------------------
    // Streak / activity-count / challenge specific getters and setters
    // -------------------------------------------------------------------------

    /**
     * AC: streakRequired getter/setter round-trip.
     */
    @Test
    public void streakRequired_setAndGet() {
        Badge b = makeBadge();
        b.setStreakRequired(7);
        assertEquals(7, b.getStreakRequired());
    }

    /**
     * AC: activityCountRequired getter/setter round-trip.
     */
    @Test
    public void activityCountRequired_setAndGet() {
        Badge b = makeBadge();
        b.setActivityCountRequired(10);
        assertEquals(10, b.getActivityCountRequired());
    }

    /**
     * AC: activityType getter/setter round-trip.
     */
    @Test
    public void activityType_setAndGet() {
        Badge b = makeBadge();
        b.setActivityType(ActivityLog.TRANSPORT_BIKE);
        assertEquals(ActivityLog.TRANSPORT_BIKE, b.getActivityType());
    }

    /**
     * AC: challengeId getter/setter round-trip.
     */
    @Test
    public void challengeId_setAndGet() {
        Badge b = makeBadge();
        b.setChallengeId("challenge-xyz");
        assertEquals("challenge-xyz", b.getChallengeId());
    }

    /**
     * AC: challengePercentage getter/setter round-trip.
     */
    @Test
    public void challengePercentage_setAndGet() {
        Badge b = makeBadge();
        b.setChallengePercentage(75.0);
        assertEquals(75.0, b.getChallengePercentage(), 1e-9);
    }

    /**
     * AC: isHardcoded defaults to false for a Badge created via the 6-arg constructor.
     */
    @Test
    public void isHardcoded_defaultFalse() {
        assertFalse(makeBadge().isHardcoded());
    }

    /**
     * AC: isHardcoded can be set to true.
     */
    @Test
    public void isHardcoded_setToTrue_returnsTrue() {
        Badge b = makeBadge();
        b.setHardcoded(true);
        assertTrue(b.isHardcoded());
    }

    /**
     * AC: No-arg constructor creates an instance (all fields null/0/false by default).
     */
    @Test
    public void noArgConstructor_createsNonNullInstance() {
        Badge b = new Badge();
        assertNull(b.getBadgeId());
        assertNull(b.getName());
        assertNull(b.getStatus());
        assertEquals(0, b.getPointThreshold());
        assertFalse(b.isEarned());
    }

    /**
     * AC: badgeType is set to TYPE_POINT_THRESHOLD in the 6-arg constructor.
     */
    @Test
    public void constructor_setsDefaultBadgeTypeToPointThreshold() {
        assertEquals(Badge.TYPE_POINT_THRESHOLD, makeBadge().getBadgeType());
    }

    /**
     * AC: badgeTier is set to TIER_BASIC in the 6-arg constructor.
     */
    @Test
    public void constructor_setsDefaultBadgeTierToBasic() {
        assertEquals(Badge.TIER_BASIC, makeBadge().getBadgeTier());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Badge makeBadge() {
        return new Badge("badge-1", "Test Badge", "Test desc", "Test criteria", "🏅", 100);
    }
}
