package com.example.ecolums;

import java.util.ArrayList;
import java.util.List;

/**
 * Hardcoded starter badge definitions.
 * These are seeded into Firestore by the admin via the "Seed Defaults" button.
 * They are BASIC tier badges intended to keep new users engaged.
 */
public class BadgeDefinitions {

    // Badge ID constants
    public static final String ID_FIRST_STEP      = "first_step";
    public static final String ID_CLUB_MEMBER     = "club_member";
    public static final String ID_CHALLENGE_JOINER = "challenge_joiner";
    public static final String ID_WEEK_WARRIOR    = "week_warrior";
    public static final String ID_MONTH_CHAMP     = "month_champ";
    public static final String ID_ECO_STARTER     = "eco_starter";
    public static final String ID_GREEN_HUNDRED   = "green_hundred";
    public static final String ID_BIKE_FIVE       = "bike_five";

    private BadgeDefinitions() {}

    /** Returns all hardcoded starter badges. */
    public static List<Badge> getHardcodedBadges() {
        List<Badge> badges = new ArrayList<>();

        // First activity
        Badge firstStep = new Badge(ID_FIRST_STEP, "First Step",
                "Logged your very first eco activity!", "Log 1 activity", "🌱", 0);
        firstStep.setBadgeType(Badge.TYPE_HARDCODED);
        firstStep.setBadgeTier(Badge.TIER_BASIC);
        firstStep.setHardcoded(true);
        badges.add(firstStep);

        // Joined a club
        Badge clubMember = new Badge(ID_CLUB_MEMBER, "Club Member",
                "You joined an eco club. Teamwork makes the dream work!", "Join a club", "🤝", 0);
        clubMember.setBadgeType(Badge.TYPE_HARDCODED);
        clubMember.setBadgeTier(Badge.TIER_BASIC);
        clubMember.setHardcoded(true);
        badges.add(clubMember);

        // Joined a challenge
        Badge challengeJoiner = new Badge(ID_CHALLENGE_JOINER, "Challenge Accepted",
                "Enrolled in your first sustainability challenge!", "Join a challenge", "🎯", 0);
        challengeJoiner.setBadgeType(Badge.TYPE_HARDCODED);
        challengeJoiner.setBadgeTier(Badge.TIER_BASIC);
        challengeJoiner.setHardcoded(true);
        badges.add(challengeJoiner);

        // 7-day streak
        Badge weekWarrior = new Badge(ID_WEEK_WARRIOR, "Week Warrior",
                "7 days of consecutive eco activity logging!", "Maintain a 7-day streak", "🔥", 0);
        weekWarrior.setBadgeType(Badge.TYPE_STREAK);
        weekWarrior.setBadgeTier(Badge.TIER_BASIC);
        weekWarrior.setStreakRequired(7);
        weekWarrior.setHardcoded(true);
        badges.add(weekWarrior);

        // 30-day streak
        Badge monthChamp = new Badge(ID_MONTH_CHAMP, "Month Champion",
                "30 consecutive days of eco logging — incredible dedication!", "Maintain a 30-day streak", "🏅", 0);
        monthChamp.setBadgeType(Badge.TYPE_STREAK);
        monthChamp.setBadgeTier(Badge.TIER_BASIC);
        monthChamp.setStreakRequired(30);
        monthChamp.setHardcoded(true);
        badges.add(monthChamp);

        // 50 points
        Badge ecoStarter = new Badge(ID_ECO_STARTER, "Eco Starter",
                "Earned your first 50 green points!", "Earn 50 points", "💚", 50);
        ecoStarter.setBadgeType(Badge.TYPE_POINT_THRESHOLD);
        ecoStarter.setBadgeTier(Badge.TIER_BASIC);
        ecoStarter.setHardcoded(true);
        badges.add(ecoStarter);

        // 100 points
        Badge greenHundred = new Badge(ID_GREEN_HUNDRED, "Green Century",
                "Surpassed 100 green points — you're on fire!", "Earn 100 points", "💯", 100);
        greenHundred.setBadgeType(Badge.TYPE_POINT_THRESHOLD);
        greenHundred.setBadgeTier(Badge.TIER_BASIC);
        greenHundred.setHardcoded(true);
        badges.add(greenHundred);

        // 5 bike trips
        Badge bikeFive = new Badge(ID_BIKE_FIVE, "Pedal Power",
                "Logged 5 bike trips — keep rolling!", "Log 5 bike trips", "🚴", 0);
        bikeFive.setBadgeType(Badge.TYPE_ACTIVITY_COUNT);
        bikeFive.setBadgeTier(Badge.TIER_BASIC);
        bikeFive.setActivityCountRequired(5);
        bikeFive.setActivityType("BIKE");
        bikeFive.setHardcoded(true);
        badges.add(bikeFive);

        return badges;
    }
}
