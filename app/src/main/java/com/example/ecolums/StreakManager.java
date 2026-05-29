package com.example.ecolums;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/** Manages daily activity streaks for users. */
public class StreakManager {

    /**
     * Updates the streak for the given user after a new activity is logged.
     * Increments streak if last activity was yesterday, resets if older, keeps if same day.
     * Persists updated streak and lastActivityDate to Firestore.
     */
    public static void updateStreak(User user) {
        if (user == null || user.getUserId() == null) return;

        Timestamp now = Timestamp.now();
        Timestamp last = user.getLastActivityDate();

        int newStreak = user.getCurrentStreak();

        if (last == null) {
            // First ever activity
            newStreak = 1;
        } else {
            long diffMillis = now.toDate().getTime() - last.toDate().getTime();
            long diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis);

            Calendar nowCal = Calendar.getInstance();
            nowCal.setTime(now.toDate());

            Calendar lastCal = Calendar.getInstance();
            lastCal.setTime(last.toDate());

            // Normalize to day boundaries
            int nowDay = nowCal.get(Calendar.DAY_OF_YEAR);
            int lastDay = lastCal.get(Calendar.DAY_OF_YEAR);
            int nowYear = nowCal.get(Calendar.YEAR);
            int lastYear = lastCal.get(Calendar.YEAR);

            if (nowYear == lastYear && nowDay == lastDay) {
                // Same day — no change to streak
                return;
            } else if (nowYear == lastYear ? (nowDay - lastDay == 1)
                    : (lastDay == getLastDayOfYear(lastYear) && nowDay == 1)) {
                // Consecutive calendar days (including Dec 31 → Jan 1) — increment
                newStreak = user.getCurrentStreak() + 1;
            } else {
                // Gap of more than one day — reset
                newStreak = 1;
            }
        }

        user.setCurrentStreak(newStreak);
        user.setLastActivityDate(now);

        // Persist to Firestore
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(user.getUserId())
                .update("currentStreak", newStreak, "lastActivityDate", now);

        // Check streak badges
        new ProgressTracker().checkStreakBadges(user, newStreak);
    }

    private static int getLastDayOfYear(int year) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, Calendar.DECEMBER);
        cal.set(Calendar.DAY_OF_MONTH, 31);
        return cal.get(Calendar.DAY_OF_YEAR);
    }
}
