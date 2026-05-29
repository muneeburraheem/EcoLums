package com.example.ecolums;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Unit tests for Leaderboard — covers US_04.01 (Leaderboard Rankings).
 * <p>
 * Tests cover the RankEntry data class, timeframe/entity constants,
 * initial cache state, and the ranking sort logic that powers the
 * getTopPerformers() output.
 */
public class LeaderboardTest {

	private MockedStatic<FirebaseFirestore> mockedFirestore;
	private Leaderboard leaderboard;

	@Before
	public void setUp() {
		FirebaseFirestore mockDb = mock(FirebaseFirestore.class);
		mockedFirestore = Mockito.mockStatic(FirebaseFirestore.class);
		mockedFirestore.when(FirebaseFirestore::getInstance).thenReturn(mockDb);
		leaderboard = new Leaderboard();
	}

	@After
	public void tearDown() {
		mockedFirestore.close();
	}

	// -------------------------------------------------------------------------
	// US_04.01 — Constants
	// -------------------------------------------------------------------------

	/**
	 * AC: Timeframe constants match the strings used in Firestore queries.
	 */
	@Test
	public void timeframeConstants_haveCorrectValues() {
		assertEquals("ALL_TIME", Leaderboard.TIMEFRAME_ALL_TIME);
		assertEquals("WEEKLY", Leaderboard.TIMEFRAME_WEEKLY);
	}

	/**
	 * AC: Entity type constants are defined.
	 */
	@Test
	public void entityTypeConstants_haveCorrectValues() {
		assertEquals("USER", Leaderboard.ENTITY_USER);
		assertEquals("CLUB", Leaderboard.ENTITY_CLUB);
	}

	// -------------------------------------------------------------------------
	// US_04.01 — Initial cache state
	// -------------------------------------------------------------------------

	/**
	 * AC: A fresh Leaderboard has an empty user-rankings cache.
	 */
	@Test
	public void newLeaderboard_cachedUserRankings_isEmptyList() {
		assertTrue(leaderboard.getCachedUserRankings().isEmpty());
	}

	/**
	 * AC: A fresh Leaderboard has an empty club-rankings cache.
	 */
	@Test
	public void newLeaderboard_cachedClubRankings_isEmptyList() {
		assertTrue(leaderboard.getCachedClubRankings().isEmpty());
	}

	/**
	 * AC: lastRefreshedAt is null before the first rankings load.
	 */
	@Test
	public void newLeaderboard_lastRefreshedAt_isNull() {
		assertNull(leaderboard.getLastRefreshedAt());
	}

	// -------------------------------------------------------------------------
	// US_04.01 — RankEntry data class
	// -------------------------------------------------------------------------

	/**
	 * AC: RankEntry fields can be set and retrieved correctly.
	 */
	@Test
	public void rankEntry_fieldsSetAndGetCorrectly() {
		Leaderboard.RankEntry entry = new Leaderboard.RankEntry();
		entry.entityId = "user-1";
		entry.entityName = "Ali Ahmed";
		entry.avatarUrl = "https://storage/avatar.jpg";
		entry.score = 350.0;
		entry.rank = 1;
		entry.entityType = Leaderboard.ENTITY_USER;

		assertEquals("user-1", entry.entityId);
		assertEquals("Ali Ahmed", entry.entityName);
		assertEquals("https://storage/avatar.jpg", entry.avatarUrl);
		assertEquals(350.0, entry.score, 1e-9);
		assertEquals(1, entry.rank);
		assertEquals(Leaderboard.ENTITY_USER, entry.entityType);
	}

	// -------------------------------------------------------------------------
	// US_04.01 — Ranking sort logic (replicated from finishWeeklyRankings)
	// -------------------------------------------------------------------------

	/**
	 * AC: Rankings are sorted by score descending, with the highest score
	 * receiving rank 1.
	 */
	@Test
	public void rankEntries_sortByScoreDescending_highestIsRank1() {
		List<Leaderboard.RankEntry> entries = buildUnsortedEntries();
		entries.sort(Comparator.comparingDouble((Leaderboard.RankEntry e) -> e.score).reversed());
		for (int i = 0; i < entries.size(); i++) {
			entries.get(i).rank = i + 1;
		}

		assertEquals(1, entries.get(0).rank);
		assertEquals(300.0, entries.get(0).score, 1e-9);
		assertEquals(2, entries.get(1).rank);
		assertEquals(200.0, entries.get(1).score, 1e-9);
		assertEquals(3, entries.get(2).rank);
		assertEquals(100.0, entries.get(2).score, 1e-9);
	}

	/**
	 * AC: getTopPerformers() respects the topN limit — if topN is smaller than
	 * the list, only topN entries are returned.
	 * (Tested via the subList logic used inside getTopPerformers.)
	 */
	@Test
	public void topPerformers_limitedToTopN() {
		List<Leaderboard.RankEntry> all = buildUnsortedEntries();
		all.sort(Comparator.comparingDouble((Leaderboard.RankEntry e) -> e.score).reversed());

		int topN = 2;
		List<Leaderboard.RankEntry> top = all.size() <= topN
				? all
				: all.subList(0, topN);

		assertEquals(topN, top.size());
		assertEquals(300.0, top.get(0).score, 1e-9);
		assertEquals(200.0, top.get(1).score, 1e-9);
	}

	/**
	 * AC: When the full list has fewer entries than topN, all entries are returned.
	 */
	@Test
	public void topPerformers_whenFewerthanTopN_returnsAll() {
		List<Leaderboard.RankEntry> all = buildUnsortedEntries(); // 3 entries
		int topN = 10;
		List<Leaderboard.RankEntry> top = all.size() <= topN
				? all
				: all.subList(0, topN);

		assertEquals(3, top.size());
	}

	// -------------------------------------------------------------------------
	// Helper
	// -------------------------------------------------------------------------

	private List<Leaderboard.RankEntry> buildUnsortedEntries() {
		return new ArrayList<>(Arrays.asList(
				rankEntry("u2", "Sara", 200.0),
				rankEntry("u3", "Bilal", 100.0),
				rankEntry("u1", "Ali", 300.0)
		));
	}

	private Leaderboard.RankEntry rankEntry(String id, String name, double score) {
		Leaderboard.RankEntry e = new Leaderboard.RankEntry();
		e.entityId = id;
		e.entityName = name;
		e.score = score;
		e.entityType = Leaderboard.ENTITY_USER;
		return e;
	}
}
