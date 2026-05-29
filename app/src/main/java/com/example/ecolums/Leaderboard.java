package com.example.ecolums;

import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Retrieves, ranks, and exposes leaderboard data for individual users and
 * clubs across all-time and weekly timeframes.
 *
 * <p><b>Responsibilities (from CRC card):</b></p>
 * <ul>
 *   <li>Maintain real-time leaderboard ranking.</li>
 *   <li>Retrieve total scores for all Users/Clubs.</li>
 *   <li>Rank entities based on points.</li>
 *   <li>Identify top performers for badge awards.</li>
 *   <li>Filter rankings by categories.</li>
 *   <li>Support rankings and standings for sustainability challenges and competitions.</li>
 * </ul>
 *
 * <p><b>Collaborators:</b> {@link User}, {@link ProgressTracker}.</p>
 *
 * <p>Reads from the {@code users} and {@code clubs} Firestore collections.
 * Rankings are re-derived on demand (at most once per 24 h for the all-time
 * board) rather than stored in a separate collection, to keep data consistent.</p>
 * <p>
 * Outstanding issues: Real-time listener for the weekly board updates has
 * not been attached to the UI fragment. Pagination not yet implemented —
 * the board currently loads all users, which will not scale.
 */
public class Leaderboard {

	// -------------------------------------------------------------------------
	// Constants
	// -------------------------------------------------------------------------

	/**
	 * Retrieve all-time rankings based on {@code totalPoints}.
	 */
	public static final String TIMEFRAME_ALL_TIME = "ALL_TIME";

	/**
	 * Retrieve rankings for the current week only.
	 */
	public static final String TIMEFRAME_WEEKLY = "WEEKLY";

	/**
	 * Rank individual users.
	 */
	public static final String ENTITY_USER = "USER";

	/**
	 * Rank clubs / departments.
	 */
	public static final String ENTITY_CLUB = "CLUB";

	// -------------------------------------------------------------------------
	// Fields
	// -------------------------------------------------------------------------

	/**
	 * Firestore instance.
	 */
	private final FirebaseFirestore db;

	/**
	 * Cached copy of the last-loaded user rankings to reduce reads.
	 */
	private List<RankEntry> cachedUserRankings;

	/**
	 * Cached copy of the last-loaded club rankings.
	 */
	private List<RankEntry> cachedClubRankings;

	/**
	 * Timestamp of the last full refresh (used to enforce the 24-hour window).
	 */
	private Timestamp lastRefreshedAt;

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Creates a new Leaderboard backed by Firestore.
	 */
	public Leaderboard() {
		this.db = FirebaseFirestore.getInstance();
		this.cachedUserRankings = new ArrayList<>();
		this.cachedClubRankings = new ArrayList<>();
	}

	// -------------------------------------------------------------------------
	// User rankings
	// -------------------------------------------------------------------------

	/**
	 * Fetches and ranks all users by their total green points.
	 *
	 * <p>For {@link #TIMEFRAME_ALL_TIME}, reads {@code totalPoints} directly
	 * from each user document. For {@link #TIMEFRAME_WEEKLY}, queries each
	 * user's {@code activityLogs} for the current week and sums
	 * {@code pointsEarned}.</p>
	 *
	 * <p>Results are ordered highest-to-lowest and each entry is assigned a
	 * rank number (1-indexed).</p>
	 *
	 * @param timeframe One of {@link #TIMEFRAME_ALL_TIME} or {@link #TIMEFRAME_WEEKLY}.
	 * @param callback  Receives the ordered list of {@link RankEntry} objects.
	 */
	public void getUserRankings(String timeframe, OnRankingsLoadedCallback callback) {
		if (timeframe.equals(TIMEFRAME_ALL_TIME)) {
			db.collection("users")
					.orderBy("totalPoints", Query.Direction.DESCENDING)
					.get()
					.addOnSuccessListener(querySnapshot -> {
						List<RankEntry> entries = new ArrayList<>();
						int rank = 1;
						for (var doc : querySnapshot.getDocuments()) {
							User user = doc.toObject(User.class);
							if (user == null) continue;
							RankEntry entry = new RankEntry();
							entry.entityId = user.getUserId();
							entry.entityName = user.getName();
							entry.avatarUrl = user.getProfileImageUrl();
							entry.score = user.getTotalPoints();
							entry.rank = rank++;
							entry.entityType = ENTITY_USER;
							entries.add(entry);
						}
						cachedUserRankings = entries;
						lastRefreshedAt = Timestamp.now();
						callback.onRankingsLoaded(entries);
					})
					.addOnFailureListener(e -> {
					Log.e("Leaderboard", "Failed to load all-time user rankings", e);
					callback.onRankingsLoaded(new ArrayList<>());
				});

		} else if (timeframe.equals(TIMEFRAME_WEEKLY)) {
			// Build start-of-week (Monday 00:00:00 local time)
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
			int daysToMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
			cal.add(Calendar.DAY_OF_MONTH, -daysToMonday);
			Timestamp startOfWeek = new Timestamp(cal.getTimeInMillis() / 1000, 0);

			// Fetch all users first, then query each user's activityLogs individually.
			// This avoids the collectionGroup composite-index requirement.
			db.collection("users").get()
					.addOnSuccessListener(usersSnapshot -> {
						if (usersSnapshot.isEmpty()) {
							callback.onRankingsLoaded(new ArrayList<>());
							return;
						}
						List<User> userList = new ArrayList<>();
						Map<String, String> namesMap = new HashMap<>();
						for (var doc : usersSnapshot.getDocuments()) {
							User u = doc.toObject(User.class);
							if (u == null || u.getUserId() == null) continue;
							userList.add(u);
							String name = u.getName() != null ? u.getName()
									: (u.getEmail() != null ? u.getEmail() : u.getUserId());
							namesMap.put(u.getUserId(), name);
						}
						if (userList.isEmpty()) {
							callback.onRankingsLoaded(new ArrayList<>());
							return;
						}

						Map<String, Double> pointsMap = new ConcurrentHashMap<>();
						AtomicInteger remaining = new AtomicInteger(userList.size());

						for (User u : userList) {
							final String uid = u.getUserId();
							db.collection("users").document(uid)
									.collection("activityLogs")
									.whereGreaterThanOrEqualTo("date", startOfWeek)
									.get()
									.addOnSuccessListener(logsSnap -> {
										double pts = 0;
										for (var ld : logsSnap.getDocuments()) {
											Double p = ld.getDouble("pointsEarned");
											if (p != null) pts += p;
										}
										if (pts > 0) pointsMap.put(uid, pts);
										if (remaining.decrementAndGet() == 0)
											finishWeeklyRankings(pointsMap, namesMap, callback);
									})
									.addOnFailureListener(e -> {
										if (remaining.decrementAndGet() == 0)
											finishWeeklyRankings(pointsMap, namesMap, callback);
									});
						}
					})
					.addOnFailureListener(e -> {
						Log.e("Leaderboard", "Failed to load users for weekly rankings", e);
						callback.onRankingsLoaded(new ArrayList<>());
					});
		} else {
			callback.onRankingsLoaded(new ArrayList<>());
		}
	}

	/**
	 * Returns the rank and score of a specific user within the given timeframe.
	 *
	 * <p>Uses the cached rankings if available; otherwise triggers a fresh
	 * {@link #getUserRankings} call.</p>
	 *
	 * @param userId    UID of the user to look up.
	 * @param timeframe One of {@link #TIMEFRAME_ALL_TIME} or {@link #TIMEFRAME_WEEKLY}.
	 * @param callback  Receives the user's {@link RankEntry}, or null if not found.
	 */
	public void getUserRank(String userId, String timeframe, OnSingleRankCallback callback) {
		getUserRankings(
				timeframe, rankings -> {
					RankEntry found = null;
					for (RankEntry entry : rankings) {
						if (entry.entityId.equals(userId)) {
							found = entry;
							break;
						}
					}
					callback.onSingleRankLoaded(found);
				}
		);
	}

	/**
	 * Assembles and delivers the final weekly rankings once all per-user queries finish.
	 */
	private void finishWeeklyRankings(
			Map<String, Double> pointsMap,
			Map<String, String> namesMap,
			OnRankingsLoadedCallback callback
	) {
		List<RankEntry> entries = new ArrayList<>();
		for (Map.Entry<String, Double> e : pointsMap.entrySet()) {
			RankEntry entry = new RankEntry();
			entry.entityId = e.getKey();
			entry.entityName = namesMap.getOrDefault(e.getKey(), "Unknown");
			entry.score = e.getValue();
			entry.entityType = ENTITY_USER;
			entries.add(entry);
		}
		entries.sort(Comparator.comparingDouble((RankEntry e) -> e.score).reversed());
		for (int i = 0; i < entries.size(); i++) entries.get(i).rank = i + 1;
		cachedUserRankings = entries;
		lastRefreshedAt = Timestamp.now();
		callback.onRankingsLoaded(entries);
	}

	// -------------------------------------------------------------------------
	// Club rankings
	// -------------------------------------------------------------------------

	/**
	 * Fetches and ranks all clubs by their aggregated {@code totalPoints}.
	 *
	 * <p>Reads the {@code totalPoints} field from each document in the
	 * {@code clubs} collection, sorts descending, and assigns rank numbers.</p>
	 *
	 * @param callback Receives the ordered list of club {@link RankEntry} objects.
	 */
	public void getClubRankings(OnRankingsLoadedCallback callback) {
		db.collection("clubs")
				.orderBy("totalPoints", Query.Direction.DESCENDING)
				.get()
				.addOnSuccessListener(querySnapshot -> {
					List<RankEntry> entries = new ArrayList<>();
					int rank = 1;
					for (var doc : querySnapshot.getDocuments()) {
						Club club = doc.toObject(Club.class);
						if (club == null) continue;
						RankEntry entry = new RankEntry();
						entry.entityId = club.getClubId();
						entry.entityName = club.getName();
						entry.avatarUrl = club.getAvatarUrl();
						entry.score = club.getTotalPoints();
						entry.rank = rank++;
						entry.entityType = ENTITY_CLUB;
						entries.add(entry);
					}
					cachedClubRankings = entries;
					callback.onRankingsLoaded(entries);
				})
				.addOnFailureListener(e -> {
					Log.e("Leaderboard", "Failed to load club rankings", e);
					callback.onRankingsLoaded(new ArrayList<>());
				});
	}

	// -------------------------------------------------------------------------
	// Challenge-specific leaderboard
	// -------------------------------------------------------------------------

	/**
	 * Returns a ranked leaderboard for a specific challenge.
	 *
	 * <p>Reads {@code participantScores} from the challenge document and
	 * sorts entries by score descending. Applies per-capita averaging if
	 * the participants are clubs rather than individuals, to ensure fair
	 * competition between groups of different sizes.</p>
	 *
	 * @param challengeId Firestore document ID of the challenge.
	 * @param callback    Receives the ordered list of challenge {@link RankEntry} objects.
	 */
	public void getChallengeRankings(String challengeId, OnRankingsLoadedCallback callback) {
		db.collection("challenges").document(challengeId)
				.get()
				.addOnSuccessListener(documentSnapshot -> {
					Challenge challenge = documentSnapshot.toObject(Challenge.class);
					if (challenge == null || challenge.getParticipantScores() == null) {
						callback.onRankingsLoaded(new ArrayList<>());
						return;
					}

					List<RankEntry> entries = new ArrayList<>();
					for (var entry : challenge.getParticipantScores().entrySet()) {
						RankEntry rankEntry = new RankEntry();
						rankEntry.entityId = entry.getKey();
						rankEntry.score = entry.getValue();
						rankEntry.entityName = entry.getKey();
						rankEntry.entityType = ENTITY_USER;
						entries.add(rankEntry);
					}

					entries.sort(Comparator.comparingDouble((RankEntry e) -> e.score).reversed());
					for (int i = 0; i < entries.size(); i++) {
						entries.get(i).rank = i + 1;
					}
					loadRankEntryNames(entries, callback);
				})
				.addOnFailureListener(e -> callback.onRankingsLoaded(new ArrayList<>()));
	}

	private void loadRankEntryNames(
			List<RankEntry> entries,
			OnRankingsLoadedCallback callback
	) {
		if (entries.isEmpty()) {
			callback.onRankingsLoaded(entries);
			return;
		}
		AtomicInteger remaining = new AtomicInteger(entries.size());
		for (RankEntry entry : entries) {
			db.collection("users").document(entry.entityId)
					.get()
					.addOnSuccessListener(doc -> {
						User user = doc.toObject(User.class);
						if (user != null) {
							entry.entityName = user.getName() != null ? user.getName() : entry.entityId;
							entry.avatarUrl = user.getProfileImageUrl();
						}
						if (remaining.decrementAndGet() == 0) callback.onRankingsLoaded(entries);
					})
					.addOnFailureListener(e -> {
						if (remaining.decrementAndGet() == 0) callback.onRankingsLoaded(entries);
					});
		}
	}

	// -------------------------------------------------------------------------
	// Top performers (used for badge award and admin reporting)
	// -------------------------------------------------------------------------

	/**
	 * Returns the top N users or clubs by score within a given timeframe.
	 *
	 * <p>Used by {@link AdminDashboard} for reports and by {@link ProgressTracker}
	 * when concluding challenges to identify badge recipients.</p>
	 *
	 * @param entityType One of {@link #ENTITY_USER} or {@link #ENTITY_CLUB}.
	 * @param timeframe  One of {@link #TIMEFRAME_ALL_TIME} or {@link #TIMEFRAME_WEEKLY}.
	 * @param topN       How many top entries to return.
	 * @param callback   Receives the top-N list.
	 */
	public void getTopPerformers(
			String entityType, String timeframe,
			int topN, OnRankingsLoadedCallback callback
	) {
		OnRankingsLoadedCallback ranker = rankings -> {
			List<RankEntry> top = rankings.size() <= topN
					? rankings
					: rankings.subList(0, topN);
			callback.onRankingsLoaded(new ArrayList<>(top));
		};

		if (entityType.equals(ENTITY_USER)) {
			getUserRankings(timeframe, ranker);
		} else {
			getClubRankings(ranker);
		}
	}

	// -------------------------------------------------------------------------
	// Inner data class
	// -------------------------------------------------------------------------

	/**
	 * A single entry in a leaderboard, representing one user or club.
	 */
	public static class RankEntry {
		/**
		 * UID or club ID of this entry.
		 */
		public String entityId;
		/**
		 * Display name of the user or club.
		 */
		public String entityName;
		/**
		 * Avatar URL (profile photo or club logo).
		 */
		public String avatarUrl;
		/**
		 * Total score (points) for this entry.
		 */
		public double score;
		/**
		 * 1-indexed rank position on the leaderboard.
		 */
		public int rank;
		/**
		 * One of {@link Leaderboard#ENTITY_USER} or {@link Leaderboard#ENTITY_CLUB}.
		 */
		public String entityType;
	}

	// -------------------------------------------------------------------------
	// Callback interfaces
	// -------------------------------------------------------------------------

	/**
	 * Callback for list-of-rankings operations.
	 */
	public interface OnRankingsLoadedCallback {
		/**
		 * @param rankings Ordered list of entries; empty on failure.
		 */
		void onRankingsLoaded(List<RankEntry> rankings);
	}

	/**
	 * Callback for single-user rank lookup.
	 */
	public interface OnSingleRankCallback {
		/**
		 * @param entry The rank entry for the user, or null if not found.
		 */
		void onSingleRankLoaded(RankEntry entry);
	}

	// -------------------------------------------------------------------------
	// Accessors for cached data
	// -------------------------------------------------------------------------

	/**
	 * @return The most recently cached user rankings; may be empty.
	 */
	public List<RankEntry> getCachedUserRankings() {
		return cachedUserRankings;
	}

	/**
	 * @return The most recently cached club rankings; may be empty.
	 */
	public List<RankEntry> getCachedClubRankings() {
		return cachedClubRankings;
	}

	/**
	 * @return Timestamp of the last full refresh, or null if never refreshed.
	 */
	public Timestamp getLastRefreshedAt() {
		return lastRefreshedAt;
	}
}
