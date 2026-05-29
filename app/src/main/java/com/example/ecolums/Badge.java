package com.example.ecolums;

import com.google.firebase.Timestamp;

/**
 * Represents an achievement badge that a user can earn by meeting specific
 * sustainability milestones or completing challenges.
 *
 * <p>Badges are stored as a sub-collection under each user's Firestore document
 * and are also referenced from {@link ProgressTracker} when milestone checks run.</p>
 */
public class Badge {

    // -------------------------------------------------------------------------
    // Constants — status
    // -------------------------------------------------------------------------

    public static final String STATUS_EARNED   = "EARNED";
    public static final String STATUS_UNEARNED = "UNEARNED";

    // -------------------------------------------------------------------------
    // Constants — badge type
    // -------------------------------------------------------------------------

    public static final String TYPE_POINT_THRESHOLD      = "POINT_THRESHOLD";
    public static final String TYPE_STREAK               = "STREAK";
    public static final String TYPE_ACTIVITY_COUNT       = "ACTIVITY_COUNT";
    public static final String TYPE_CHALLENGE_COMPLETION = "CHALLENGE_COMPLETION";
    public static final String TYPE_CHALLENGE_PERCENTAGE = "CHALLENGE_PERCENTAGE";
    public static final String TYPE_HARDCODED            = "HARDCODED";

    // -------------------------------------------------------------------------
    // Constants — badge tier
    // -------------------------------------------------------------------------

    /** Starter/hardcoded badges — simpler visual style. */
    public static final String TIER_BASIC   = "BASIC";

    /** Admin-created milestone badges — gold accent, premium styling. */
    public static final String TIER_PREMIUM = "PREMIUM";

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private String badgeId;
    private String name;
    private String description;
    private String criteria;

    /**
     * Stores either a Firebase Storage URL or a single emoji character.
     * For new badges created via the admin panel, this holds the emoji.
     */
    private String iconUrl;

    private String status;
    private Timestamp dateEarned;

    /** Points threshold for TYPE_POINT_THRESHOLD badges. 0 if action-triggered. */
    private int pointThreshold;

    /** Badge type — one of the TYPE_* constants. */
    private String badgeType;

    /** Badge tier — TIER_BASIC or TIER_PREMIUM. */
    private String badgeTier;

    /** Required consecutive days for TYPE_STREAK badges. */
    private int streakRequired;

    /** Required activity count for TYPE_ACTIVITY_COUNT badges. */
    private int activityCountRequired;

    /**
     * Transport/waste mode for TYPE_ACTIVITY_COUNT badges
     * (e.g. "BIKE", "RECYCLE"). Null means any activity.
     */
    private String activityType;

    /** Challenge Firestore doc ID for TYPE_CHALLENGE_* badges. */
    private String challengeId;

    /** Percentage threshold (0–100) for TYPE_CHALLENGE_PERCENTAGE badges. */
    private double challengePercentage;

    /** True if this is a hardcoded starter badge seeded by the system. */
    private boolean isHardcoded;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public Badge() {}

    public Badge(String badgeId, String name, String description,
                 String criteria, String iconUrl, int pointThreshold) {
        this.badgeId = badgeId;
        this.name = name;
        this.description = description;
        this.criteria = criteria;
        this.iconUrl = iconUrl;
        this.pointThreshold = pointThreshold;
        this.status = STATUS_UNEARNED;
        this.dateEarned = null;
        this.badgeType = TYPE_POINT_THRESHOLD;
        this.badgeTier = TIER_BASIC;
    }

    // -------------------------------------------------------------------------
    // Convenience methods
    // -------------------------------------------------------------------------

    public boolean isEarned() {
        return STATUS_EARNED.equals(status);
    }

    public boolean isPremium() {
        return TIER_PREMIUM.equals(badgeTier);
    }

    /**
     * Returns the emoji to display for this badge.
     * Uses iconUrl if it's a single emoji, otherwise returns a fallback.
     */
    public String getDisplayEmoji() {
        if (iconUrl != null && iconUrl.length() <= 4) return iconUrl; // emoji
        // Fallback by type
        if (TYPE_STREAK.equals(badgeType))               return "🔥";
        if (TYPE_ACTIVITY_COUNT.equals(badgeType))       return "⭐";
        if (TYPE_CHALLENGE_COMPLETION.equals(badgeType)) return "🏆";
        if (TYPE_CHALLENGE_PERCENTAGE.equals(badgeType)) return "📊";
        if (TYPE_POINT_THRESHOLD.equals(badgeType))      return "💚";
        return "🌿";
    }

    // -------------------------------------------------------------------------
    // Getters and Setters
    // -------------------------------------------------------------------------

    public String getBadgeId()             { return badgeId; }
    public void   setBadgeId(String v)     { badgeId = v; }

    public String getName()                { return name; }
    public void   setName(String v)        { name = v; }

    public String getDescription()         { return description; }
    public void   setDescription(String v) { description = v; }

    public String getCriteria()            { return criteria; }
    public void   setCriteria(String v)    { criteria = v; }

    public String getIconUrl()             { return iconUrl; }
    public void   setIconUrl(String v)     { iconUrl = v; }

    public String getStatus()              { return status; }
    public void   setStatus(String v)      { status = v; }

    public Timestamp getDateEarned()       { return dateEarned; }
    public void      setDateEarned(Timestamp v) { dateEarned = v; }

    public int  getPointThreshold()        { return pointThreshold; }
    public void setPointThreshold(int v)   { pointThreshold = v; }

    public String getBadgeType()           { return badgeType; }
    public void   setBadgeType(String v)   { badgeType = v; }

    public String getBadgeTier()           { return badgeTier; }
    public void   setBadgeTier(String v)   { badgeTier = v; }

    public int  getStreakRequired()        { return streakRequired; }
    public void setStreakRequired(int v)   { streakRequired = v; }

    public int  getActivityCountRequired() { return activityCountRequired; }
    public void setActivityCountRequired(int v) { activityCountRequired = v; }

    public String getActivityType()        { return activityType; }
    public void   setActivityType(String v){ activityType = v; }

    public String getChallengeId()         { return challengeId; }
    public void   setChallengeId(String v) { challengeId = v; }

    public double getChallengePercentage()        { return challengePercentage; }
    public void   setChallengePercentage(double v){ challengePercentage = v; }

    public boolean isHardcoded()           { return isHardcoded; }
    public void    setHardcoded(boolean v) { isHardcoded = v; }
}
