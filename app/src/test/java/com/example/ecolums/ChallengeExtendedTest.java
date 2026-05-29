package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extended unit tests for Challenge — covers participantIds, participantScores,
 * dates, rewardBadgeId, notificationMilestones, and fields not covered by the
 * primary ChallengeTest suite.
 * <p>
 * Challenge is a pure-Java model with no Firebase dependencies.
 */
public class ChallengeExtendedTest {

    // -------------------------------------------------------------------------
    // No-arg constructor
    // -------------------------------------------------------------------------

    /**
     * AC: No-arg constructor does not throw and all fields are null/0/false.
     */
    @Test
    public void noArgConstructor_allFieldsAreNullOrDefault() {
        Challenge c = new Challenge();
        assertNull(c.getChallengeId());
        assertNull(c.getTitle());
        assertNull(c.getDescription());
        assertNull(c.getCreatorId());
        assertNull(c.getStatus());
        assertNull(c.getGoalType());
        assertEquals(0.0, c.getTargetValue(), 1e-9);
        assertNull(c.getTargetUnit());
        assertNull(c.getStartDate());
        assertNull(c.getEndDate());
        assertNull(c.getParticipantIds());
        assertNull(c.getParticipantScores());
        assertNull(c.getRewardBadgeId());
        assertNull(c.getNotificationMilestones());
        assertNull(c.getInviteCode());
        assertFalse(c.isManualDeactivated());
    }

    // -------------------------------------------------------------------------
    // participantIds
    // -------------------------------------------------------------------------

    /**
     * AC: participantIds can be set and retrieved.
     */
    @Test
    public void participantIds_setAndGet() {
        Challenge c = makeChallenge();
        List<String> ids = Arrays.asList("user-1", "user-2", "user-3");
        c.setParticipantIds(ids);
        assertEquals(3, c.getParticipantIds().size());
        assertTrue(c.getParticipantIds().contains("user-1"));
        assertTrue(c.getParticipantIds().contains("user-3"));
    }

    /**
     * AC: participantIds can be set to an empty list.
     */
    @Test
    public void participantIds_emptyList_setAndGet() {
        Challenge c = makeChallenge();
        c.setParticipantIds(Arrays.asList());
        assertTrue(c.getParticipantIds().isEmpty());
    }

    /**
     * AC: participantIds can be set to null.
     */
    @Test
    public void participantIds_setToNull() {
        Challenge c = makeChallenge();
        c.setParticipantIds(null);
        assertNull(c.getParticipantIds());
    }

    // -------------------------------------------------------------------------
    // participantScores
    // -------------------------------------------------------------------------

    /**
     * AC: participantScores can be set and retrieved.
     */
    @Test
    public void participantScores_setAndGet() {
        Challenge c = makeChallenge();
        Map<String, Double> scores = new HashMap<>();
        scores.put("user-1", 150.0);
        scores.put("user-2", 75.0);
        c.setParticipantScores(scores);
        assertEquals(2, c.getParticipantScores().size());
        assertEquals(150.0, c.getParticipantScores().get("user-1"), 1e-9);
        assertEquals(75.0, c.getParticipantScores().get("user-2"), 1e-9);
    }

    /**
     * AC: A score of zero is valid (participant just joined).
     */
    @Test
    public void participantScores_zeroScore_isValid() {
        Challenge c = makeChallenge();
        Map<String, Double> scores = new HashMap<>();
        scores.put("new-user", 0.0);
        c.setParticipantScores(scores);
        assertEquals(0.0, c.getParticipantScores().get("new-user"), 1e-9);
    }

    /**
     * AC: Scores map can be updated (overwritten).
     */
    @Test
    public void participantScores_canBeOverwritten() {
        Challenge c = makeChallenge();
        Map<String, Double> first = new HashMap<>();
        first.put("u1", 10.0);
        c.setParticipantScores(first);

        Map<String, Double> second = new HashMap<>();
        second.put("u1", 50.0);
        c.setParticipantScores(second);

        assertEquals(50.0, c.getParticipantScores().get("u1"), 1e-9);
    }

    // -------------------------------------------------------------------------
    // Start/end dates
    // -------------------------------------------------------------------------

    /**
     * AC: startDate can be set and retrieved.
     */
    @Test
    public void startDate_setAndGet() {
        Challenge c = makeChallenge();
        Timestamp ts = new Timestamp(1_700_000_000L, 0);
        c.setStartDate(ts);
        assertEquals(ts, c.getStartDate());
    }

    /**
     * AC: endDate can be set and retrieved.
     */
    @Test
    public void endDate_setAndGet() {
        Challenge c = makeChallenge();
        Timestamp ts = new Timestamp(1_702_000_000L, 0);
        c.setEndDate(ts);
        assertEquals(ts, c.getEndDate());
    }

    /**
     * AC: endDate can be after startDate (valid time range).
     */
    @Test
    public void dates_endDateAfterStartDate_isValid() {
        Timestamp start = new Timestamp(1_700_000_000L, 0);
        Timestamp end = new Timestamp(1_702_000_000L, 0);
        assertTrue(end.toDate().after(start.toDate()));

        Challenge c = makeChallenge();
        c.setStartDate(start);
        c.setEndDate(end);
        assertTrue(c.getEndDate().toDate().after(c.getStartDate().toDate()));
    }

    // -------------------------------------------------------------------------
    // rewardBadgeId
    // -------------------------------------------------------------------------

    /**
     * AC: rewardBadgeId is null by default.
     */
    @Test
    public void rewardBadgeId_defaultNull() {
        assertNull(makeChallenge().getRewardBadgeId());
    }

    /**
     * AC: rewardBadgeId can be set and retrieved.
     */
    @Test
    public void rewardBadgeId_setAndGet() {
        Challenge c = makeChallenge();
        c.setRewardBadgeId("badge-eco-star");
        assertEquals("badge-eco-star", c.getRewardBadgeId());
    }

    // -------------------------------------------------------------------------
    // Setters for all constructor-set fields
    // -------------------------------------------------------------------------

    /**
     * AC: setTitle / getTitle round-trip.
     */
    @Test
    public void title_setAndGet() {
        Challenge c = makeChallenge();
        c.setTitle("New Title");
        assertEquals("New Title", c.getTitle());
    }

    /**
     * AC: setDescription / getDescription round-trip.
     */
    @Test
    public void description_setAndGet() {
        Challenge c = makeChallenge();
        c.setDescription("Updated desc");
        assertEquals("Updated desc", c.getDescription());
    }

    /**
     * AC: setCreatorId / getCreatorId round-trip.
     */
    @Test
    public void creatorId_setAndGet() {
        Challenge c = makeChallenge();
        c.setCreatorId("new-creator");
        assertEquals("new-creator", c.getCreatorId());
    }

    /**
     * AC: setGoalType / getGoalType round-trip.
     */
    @Test
    public void goalType_setAndGet() {
        Challenge c = makeChallenge();
        c.setGoalType(Challenge.GOAL_TYPE_ENERGY);
        assertEquals(Challenge.GOAL_TYPE_ENERGY, c.getGoalType());
    }

    /**
     * AC: setTargetValue / getTargetValue round-trip.
     */
    @Test
    public void targetValue_setAndGet() {
        Challenge c = makeChallenge();
        c.setTargetValue(250.5);
        assertEquals(250.5, c.getTargetValue(), 1e-9);
    }

    /**
     * AC: setTargetUnit / getTargetUnit round-trip.
     */
    @Test
    public void targetUnit_setAndGet() {
        Challenge c = makeChallenge();
        c.setTargetUnit("kWh");
        assertEquals("kWh", c.getTargetUnit());
    }

    /**
     * AC: notificationMilestones can be updated.
     */
    @Test
    public void notificationMilestones_setAndGet() {
        Challenge c = makeChallenge();
        c.setNotificationMilestones(Arrays.asList(25, 50, 75, 100));
        assertEquals(4, c.getNotificationMilestones().size());
        assertEquals(Integer.valueOf(25), c.getNotificationMilestones().get(0));
    }

    /**
     * AC: challengeId can be updated after construction.
     */
    @Test
    public void challengeId_setAndGet() {
        Challenge c = makeChallenge();
        c.setChallengeId("new-challenge-id");
        assertEquals("new-challenge-id", c.getChallengeId());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Challenge makeChallenge() {
        return new Challenge(
                "cid", "Bike Week", "Ride bikes",
                "leader-uid", Challenge.GOAL_TYPE_TRANSPORT,
                100.0, "km", null, null, Arrays.asList(50, 100)
        );
    }
}
